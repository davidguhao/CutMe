package com.guhao.opensource.cutme.android

import androidx.lifecycle.MutableLiveData

class TracksHistoryRecorder {
    private var currentHistoryPointer = -1
    private var tracksHistory = MutableLiveData(listOf<List<Track>>())

    fun record(tracks: List<Track>) {
        if(currentHistoryPointer != tracksHistory.value!!.size - 1)
            tracksHistory.value = tracksHistory.value!!.subList(0, currentHistoryPointer + 1)
        tracksHistory.value = tracksHistory.value!! + listOf(tracks)
        currentHistoryPointer ++
    }
    fun hasPreviousTracks(): Boolean {
        return currentHistoryPointer - 1 >= 0
    }
    fun hasNextTracks(): Boolean {
        return currentHistoryPointer + 1 < tracksHistory.value!!.size
    }
    fun next(): List<Track> {
        return tracksHistory.value!![++currentHistoryPointer]
    }
    fun previous(): List<Track> {
        return tracksHistory.value!![--currentHistoryPointer]
    }
}