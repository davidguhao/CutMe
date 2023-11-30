package com.guhao.opensource.cutme

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