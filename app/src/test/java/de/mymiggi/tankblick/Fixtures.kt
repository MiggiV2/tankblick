package de.mymiggi.tankblick

/** Reads a canned API response from src/test/resources/fixtures. */
fun fixture(name: String): String =
    checkNotNull(object {}.javaClass.getResource("/fixtures/$name")) {
        "missing fixture: $name"
    }.readText()
