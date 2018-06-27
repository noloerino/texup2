package markup.lexer

import markup.parser.Token
import java.io.FileInputStream

enum class LexerContext {
    NORMAL,
    FN_CALL,
    FN_QUOTED_ARG,
    FN_LIST_ARG
}

fun lex(stream: FileInputStream): List<Token> {
    val br = stream.bufferedReader()
    var i: Int = br.read()
    var tokens = mutableListOf<Token>()
    var lineNumber = 0
    var sb = StringBuilder() // keeps track of chars in the token so far
    while (i != -1) {
        val c = i.toChar()
        if (c == '\n') {
            lineNumber++
        }

    }
    return tokens
}