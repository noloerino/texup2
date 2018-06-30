package markup

import markup.lexer.Lexer
import markup.parser.Parser
import java.io.File

fun main(args: Array<String>) {
    val fileName = args[0]
    val tokens = Lexer(File(fileName).bufferedReader()).lex()
    val bw = File("lextokens.txt").bufferedWriter()
    for (t in tokens) {
        val s = t.toString()
        bw.write(s + "\n")
    }
    bw.flush()

    val words = Parser(tokens).parse()
    val pw = File("out.tex").printWriter()
    for (i in words.indices) {
        pw.write(words[i])
        if (words[i].isNotBlank() && i + 1 < words.size && words[i + 1].isNotBlank()) {
            pw.write(" ")
        }
    }
    pw.flush()
    println("wrote file")
}