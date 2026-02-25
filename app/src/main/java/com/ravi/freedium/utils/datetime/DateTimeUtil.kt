package com.ravi.freedium.utils.datetime

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun formatTimestamp(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault())
    val instant = Instant.ofEpochMilli(timestamp)
    val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()

    return localDateTime.format(formatter)
}