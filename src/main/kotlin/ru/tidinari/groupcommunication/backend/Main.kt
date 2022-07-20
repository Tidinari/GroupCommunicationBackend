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

fun main(args: Array<String>) {
    val groups = mutableListOf(Group(), Group("ИНБО-15-20", "groupsecret"))

    embeddedServer(Netty, 5050) {
        routing {
            get("/groups") {
                call.respondText(Json.encodeToString(groups.map { g -> g.group }))
            }
        }
    }.start(true)
}

@JvmInline
@Serializable
value class User(@SerialName("id") val userHash: String)

@Serializable
data class Group(
    val group: String = "ТЕСТ-00-00",
    val groupSecret: String = "anypassword"
)

@Serializable
data class Lesson(
    val weeks: Short,
    val lessonInDay: Short,
    val name: String,
    val isImportant: Short
)