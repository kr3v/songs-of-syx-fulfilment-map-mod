package kr3v.songsofsyx.fulfilmentprovider

import java.io.FileWriter
import java.io.PrintWriter


object Config {
    const val MOD_NAME = "Fulfilment Provider"
    const val MOD_VERSION = "0.0.1"
    const val PORT = 63481
    const val MOD_DESC = "Fulfilment Provider"
}

object Log {
    private val log = PrintWriter(FileWriter("/home/dbaynak/.local/share/songsofsyx/logs/fulfilment-provider.log"))

    fun println(s: String) {
        log.println(s)
    }
}

sealed class Either<A, B> {
    data class Left<A, B>(val value: A) : Either<A, B>()
    data class Right<A, B>(val value: B) : Either<A, B>()
}
