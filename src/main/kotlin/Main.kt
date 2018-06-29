package markup

import markup.lexer.Lexer
import markup.parser.Parser
import java.io.File

// Stack extension functions
fun <T> MutableList<T>.pop(): T = removeAt(size - 1)

fun <T> MutableList<T>.push(e: T) = add(e)

fun main(args: Array<String>) {
    val bw = File("gentokens.txt").bufferedWriter()
    val tokens = Lexer(File("example.txt").bufferedReader()).lex()
    for (t in tokens) {
        val s = t.repr()
//        print(s)
        bw.write(s)
//        println(t)
    }
    bw.flush()

    val lines = Parser(tokens).parse()
    for (l in lines) {
        print(l)
    }
}