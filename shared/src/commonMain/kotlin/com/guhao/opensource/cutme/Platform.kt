package com.guhao.opensource.cutme

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform