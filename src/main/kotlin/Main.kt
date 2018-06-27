package markup

import markup.lexer.Lexer
import java.io.File

fun main(args: Array<String>) {
    for (t in Lexer(File("example.txt").inputStream()).lex())
        println(t)
}