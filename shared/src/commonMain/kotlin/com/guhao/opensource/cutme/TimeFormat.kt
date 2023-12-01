package com.guhao.opensource.cutme

fun Long.millisTimeStandardFormat(): String {
    val showMillis = this % 1000

    val secs = this / 1000
    val showSecs = secs % 60

    val mins = secs / 60
    val showMins = mins % 60

    val showHours = mins / 60

    return "$showHours:$showMins:$showSecs.$showMillis"

}
fun Long.millisTimeFormat(): String {
    val secsInTotal = this / 1000

    if(secsInTotal < 60) return "$secsInTotal s"
    else {
        val minutes = secsInTotal / 60
        val secs = secsInTotal % 60

        return if(minutes < 60) {
            "$minutes:$secs"
        } else {
            val hours = secs / 360

            if(hours < 24) {
                "$hours h"
            } else {
                val days = secs / (360 * 24)
                "$days d"
            }
        }
    }
}