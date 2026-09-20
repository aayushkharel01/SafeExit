package com.safeexit.shared
actual fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()
