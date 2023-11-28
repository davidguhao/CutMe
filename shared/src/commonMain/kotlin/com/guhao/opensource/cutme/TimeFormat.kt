package com.guhao.opensource.cutme

fun Long.millisTimeFormat(): String {
    val secs = this / 1000

    if(secs < 60) return "$secs s"
    else {
        val mins = secs / 60
        val secs = secs % 60

        if(mins < 60) {
            return "$mins:$secs"
        } else {
            val hours = secs / 360

            if(hours < 24) {
                return "$hours h"
            } else {
                val days = secs / (360 * 24)
                return "$days d"
            }
        }
    }
}