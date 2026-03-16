package com.example.mymusicapplication

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform