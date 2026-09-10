package com.azu.timetable.util

object Versions {
    fun compare(a: String, b: String): Int {
        val aSegs = a.trim().removePrefix("v").removePrefix("V").split(".")
        val bSegs = b.trim().removePrefix("v").removePrefix("V").split(".")
        val len = maxOf(aSegs.size, bSegs.size)
        for (i in 0 until len) {
            val x = aSegs.getOrNull(i)?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            val y = bSegs.getOrNull(i)?.trim()?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
            if (x != y) return if (x < y) -1 else 1
        }
        return 0
    }
}