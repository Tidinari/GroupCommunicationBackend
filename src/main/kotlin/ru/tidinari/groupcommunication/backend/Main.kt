package ru.tidinari.groupcommunication.backend

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileInputStream

fun main(args: Array<String>) {
    val schedule = FileInputStream("./schedule.xlsx")
    val sheet = WorkbookFactory.create(schedule).getSheetAt(0)

    val groups = getGroups(sheet).also {
        it.add(Group("ТЕСТ-00-00", 5))
    }
    File("./generated-data").mkdirs()
    groups.forEach {
        val file = File("./generated-data/${it.group}.json")
        val groupSchedule = getGroupSchedule(it, sheet)
        file.writeText(Json.encodeToString(groupSchedule))
    }

    embeddedServer(Netty, 5050) {
        routing {
            get("/rowsheet") {
                val row = call.parameters["row"]?.toIntOrNull() ?: 1
                val builder = StringBuilder()
                for (cells in sheet.getRow(row)) {
                    if (cells.cellType == CellType.STRING) {
                        builder.append("${cells.stringCellValue}\n")
                    }
                }
                call.respondText(builder.toString())
            }
            get("/columnsheet") {
                val column = call.parameters["column"]?.toIntOrNull() ?: 1
                val builder = StringBuilder()
                for (row in sheet) {
                    val cell: Cell? = row.getCell(column)
                    if (cell?.cellType == CellType.STRING) {
                        builder.append("${cell.stringCellValue.replace("\n", " ")}\n")
                    }
                }
                call.respondText(builder.toString())
            }
            get("/groups") {
                call.respondText(Json.encodeToString(groups))
            }
            get("/groupSchedule") {
                val group = call.parameters["group"]?.toIntOrNull() ?: 0
                call.respondText(
                    Json.encodeToString(
                        getGroupSchedule(groups[group], sheet)
                    )
                )
            }
            get("/generated-data") {
                val group = call.parameters["group"] ?: "ТЕСТ-00-00"
                if (group.contains(group)) {
                    val file = File("./generated-data/$group.json")
                    call.respondText(
                        file.readText()
                    )
                }
            }
        }
    }.start(true)
}

fun getGroups(sheet: Sheet): MutableList<Group> {
    val groups = mutableListOf<Group>()
    for (cells in sheet.getRow(1)) {
        if (cells.cellType == CellType.STRING) {
            val cellValue = cells.stringCellValue
            if (cellValue.length == 10)
                groups.add(Group(cellValue, cells.columnIndex))
        }
    }
    return groups
}

fun getGroupSchedule(group: Group, sheet: Sheet): Map<Int, List<Lesson>> {
    val schedule = mutableMapOf<Int, MutableList<Lesson>>() // Week to lesson map
    for (row in sheet) {
        if (row.rowNum in 3..74) {
            val cell = row.getCell(group.columnIndex)
            if (cell?.cellType != CellType.STRING) {
                continue
            }
            val cellValue = cell.stringCellValue.replace("\n", " ")
            if (cellValue.contains("…"))
                continue
            val activityType = row.getCell(group.columnIndex + 1).stringCellValue.split("\n")
            val teacherCell = row.getCell(group.columnIndex + 2)
            var teacher = listOf("Ошибка")
            if (teacherCell.cellType == CellType.STRING)
                 teacher = teacherCell.stringCellValue.split("\n")
            val room = row.getCell(group.columnIndex + 3).stringCellValue.split("\n")
            val parsedLesson = generateWeeklyLessons(row.rowNum, cellValue, activityType, teacher, room)
            parsedLesson.forEach {
                schedule[it.key]?.add(it.value) ?: schedule.put(it.key, mutableListOf(it.value))
            }
        }
    }
    return schedule
}

fun generateWeeklyLessons(
    rowNum: Int,
    cellValue: String,
    activityType: List<String>,
    teacher: List<String>,
    room: List<String>
): Map<Int, Lesson> {
    if (!cellValue.contains("н.") && !cellValue.contains("кр.")) {
        val weekLessonMap = mutableMapOf<Int, Lesson>()
        val weeks = generateWeekSetWithExceptOperation(rowNum, "0")
        val day = Day.getDayFromIndex(rowNum).ordinal
        val lessonInDay = LessonInDay.getLessonInDayFromIndex(rowNum).ordinal
        for (week in weeks) {
            weekLessonMap[week] = Lesson(cellValue, day, lessonInDay, activityType[0], teacher[0], room[0])
        }
        return weekLessonMap
    }
    val weekLessonMap = mutableMapOf<Int, Lesson>()
    val lessonList = Regex(" ?([кр.\\- \\d,]*) н. ?([а-яА-Я ()-]*)").findAll(cellValue)
    for ((index, lesson) in lessonList.withIndex()) {
        val lessonWeeks = lesson.groups[1]?.value ?: "20"
        val lessonTitle = lesson.groups[2]?.value ?: "Ошибка!"
        val weeks: Set<Int> = if (lessonWeeks.contains("кр.")) {
            generateWeekSetWithExceptOperation(rowNum, lessonWeeks)
        } else {
            generateWeekSetFromMultipleWeeks(lessonWeeks)
        }
        for (week in weeks) {
            weekLessonMap[week] = Lesson(
                lessonTitle,
                Day.getDayFromIndex(rowNum).ordinal,
                LessonInDay.getLessonInDayFromIndex(rowNum).ordinal,
                if (index >= activityType.size) activityType.last() else activityType[index],
                if (index >= teacher.size) teacher.last() else teacher[index],
                if (index >= room.size) teacher.last() else room[index]
            )
        }
    }
    return weekLessonMap
}

fun generateWeekSetWithExceptOperation(rowNum: Int, lessonWeeks: String): Set<Int> {
    val range = mutableSetOf<Int>()
    val isEven = isEvenWeek(rowNum)
    if (isEven) {
        range.addAll(2..18 step 2)
    } else {
        range.addAll(1..17 step 2)
    }
    val groups = Regex("(\\d*)").findAll(lessonWeeks)
    for (group in groups) {
        range.remove(group.groups[1]?.value?.toIntOrNull())
    }
    return range
}

fun generateWeekSetFromMultipleWeeks(lessonWeeks: String): Set<Int> {
    val hasDash = lessonWeeks.contains("-")
    val hasComma = lessonWeeks.contains(",")
    if (hasDash && !hasComma) {
        val dashWeeks = lessonWeeks.split("-").map { it.toInt() }
        return (dashWeeks[0]..dashWeeks[1] step 2).toSet()
    } else if (hasComma && !hasDash) {
        val commaWeeks = lessonWeeks.split(",").map { it.toInt() }
        return commaWeeks.toSet()
    } else if (hasComma && hasDash) {
        val range = mutableSetOf<Int>()
        val commaWeeks = lessonWeeks.split(",")
        for (commaValue in commaWeeks) {
            val value = commaValue.toIntOrNull()
            if (value == null) {
                range.addAll(generateWeekSetFromMultipleWeeks(commaValue))
            } else {
                range.add(value)
            }
        }
        return range
    } else {
        return Regex("(\\d*)").findAll(lessonWeeks).map {
            it.groups[1]?.value?.toIntOrNull()
        }.filterNotNull().toSet()
    }
}

fun isEvenWeek(index: Int): Boolean {
    if (index % 2 == 0) {
        return true
    }
    return false
}

@JvmInline
@Serializable
value class User(@SerialName("id") val userHash: String)

@Serializable
data class Group(
    val group: String,
    val columnIndex: Int
)

@Serializable
data class Lesson(
    val name: String,
    val day: Int,
    val lessonInDay: Int,
    val activityType: String,
    val teacher: String,
    val room: String,
)

enum class Day(private val startIndex: Short, private val endIndex: Short) {
    MONDAY(3, 14),
    TUESDAY(15, 26),
    WEDNESDAY(27, 38),
    THURSDAY(39, 50),
    FRIDAY(51, 62),
    SATURDAY(63, 74);

    companion object {
        fun getDayFromIndex(index: Int): Day {
            values().forEach {
                if (index in it.asRange()) {
                    return it
                }
            }
            throw IllegalArgumentException()
        }
    }

    fun asRange(): IntRange {
        return startIndex..endIndex
    }
}

enum class LessonInDay(private val evenRange: IntProgression, private val unEvenRange: IntProgression) {
    FIRST(3..63 step 12, 4..64 step 12),
    SECOND(5..65 step 12, 6..66 step 12),
    THIRD(7..67 step 12, 8..68 step 12),
    FORTH(9..69 step 12, 10..70 step 12),
    FIFTH(11..71 step 12, 12..72 step 12),
    SIXTH(13..73 step 12, 14..74 step 12);

    companion object {
        fun getLessonInDayFromIndex(index: Int): LessonInDay {
            LessonInDay.values().forEach {
                if (it.isInRange(index)) {
                    return it
                }
            }
            throw IllegalArgumentException()
        }
    }

    fun isInRange(index: Int): Boolean {
        return index in evenRange || index in unEvenRange
    }
}