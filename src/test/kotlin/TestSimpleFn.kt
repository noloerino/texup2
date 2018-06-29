import markup.lexer.Lexer
import markup.parser.Parser
import java.io.StringReader

// todo add junit to gradle with
// testCompile "junit ...

fun main(args: Array<String>) {
    `display fn with flat list and obj`()
}

fun `display fn with flat list and obj`() {
    val msg = """Header(a="1", b=["c", "d"], obj={ a: "a", b: "b"})"""
    val ogTokens = Lexer(StringReader(msg)).lex()
    val newTokens = Parser(ogTokens).tokens
    for (t in newTokens)
        println(t)
}
