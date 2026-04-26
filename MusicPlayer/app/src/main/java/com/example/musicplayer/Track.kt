package com.example.musicplayer

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val dataPath: String
)
