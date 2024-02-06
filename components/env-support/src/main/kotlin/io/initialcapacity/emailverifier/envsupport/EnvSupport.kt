package io.initialcapacity.emailverifier.envsupport

fun getEnvInt(name: String, default: Int): Int = System.getenv(name)?.toInt() ?: default

fun getEnvStr(name: String, default: String): String = System.getenv(name)?.toString() ?: default


