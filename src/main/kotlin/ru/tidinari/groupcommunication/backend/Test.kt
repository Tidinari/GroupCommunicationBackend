package ru.tidinari.groupcommunication.backend

fun main() {
    val regex = " ?([кр.\\- \\d,]*) н. ?([а-яА-Я ()-]*\\(?[1-9]? ?п?/?г?\\)?)".toRegex()
        .findAll("2,6,10,14 н. Технические средства автоматизации и управления (1 п/г) 4,8,12,16 н. Технические средства автоматизации и управления (2 п/г)")
    for (reg in regex) {
        println(reg.groups)
    }
}

