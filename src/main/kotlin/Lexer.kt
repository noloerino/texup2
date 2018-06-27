package markup.lexer

import markup.parser.*
import java.io.FileInputStream

enum class LexerContext {
    NORMAL, // Nothing special
    FN_CALL, // Between the parentheses of a function
    FN_QUOTED_ARG, // Inside quote marks inside a function call
    FN_LIST_ARG, // Inside [ in a function call
    FN_OBJ_ARG, // Inside { in a function call (basically a json obj)
    ESCAPE_OR_LNJOIN, // After a backslash, will either escape the next character or cause a line join
}

// Stack extension functions
fun <T> MutableList<T>.pop(): T = removeAt(size - 1)

fun <T> MutableList<T>.push(e: T) = add(e)

class Lexer(stream: FileInputStream) {
    private val br = stream.bufferedReader()
    private var i: Int = br.read()
    private var tokens = mutableListOf<Token>()
    private var lineNumber = 0
    private var sb = StringBuilder() // keeps track of chars in the token so far
    private var contextStack = mutableListOf(LexerContext.NORMAL)
    private val context
        get() = contextStack.last()

    private fun clearSB() {
        // gc is good!
        sb = StringBuilder()
    }
    // should be called on any delimiting character to signify that a token can be pushed
    private fun pushWordToken() {
        if (!sb.isEmpty()) {
            tokens.push(Word(lineNumber, sb.toString()))
            clearSB()
        }
    }

    private fun pushNewLnToken() {
        pushWordToken()
        tokens.push(NewLn(lineNumber, tokens.lastOrNull()))
    }

    private fun err(lineNumber: Int, msg: String): Nothing = throw Exception("Error during lexing: $msg (line $lineNumber)")

    // called as soon as an open paren is hit, may be recursive
    private fun processFnCall(fnName: String): FunctionCall {

        return FunctionCall(0, "dummy")
    }

    // should be called when the context is escape or lnjoin
    private fun processEscapedChar(c: Char) {
        when (c) {
            // line join
            '\\' -> {
                pushWordToken()
                tokens.push(LnJoin(lineNumber))
            }
            // '\n' -> pushNewLnToken() interferes with some control sequences
            '%' -> Word(lineNumber, "\\%")
            '{', '}' -> Word(lineNumber, c.toString())
            else -> { // just a normal LaTeX control sequence
                sb.append("\\$c")
            }
        }
    }

    fun lex(): List<Token> {
        while (i != -1) {
            val c = i.toChar()
            when (context) {
                LexerContext.NORMAL -> when (c) {
                    '\\' -> contextStack.push(LexerContext.ESCAPE_OR_LNJOIN)
                    /*
                    '(' -> {
                        if (!sb.isEmpty()) {
                            // pass on to method for function argument processing
                        }
                    TODO
                    }
                    */
                    '%' -> {
                        // Comment
                        pushWordToken()
                        i = br.read()
                        while (i != -1 && i.toChar() != '\n') {
                            sb.append(i.toChar())
                            i = br.read()
                        }
                        tokens.push(Comment(lineNumber, sb.toString()))
                        pushNewLnToken()
                        lineNumber++
                        clearSB()
                    }
                    '{' -> {
                        // Account for possibility of bad style and someone did Function{ without space
                        if (!sb.isEmpty()) {
                            tokens.push(FunctionCall(lineNumber, sb.toString()))
                            clearSB()
                        } else {
                            // Account for possibility that previous token was constructed as word
                            when (tokens.lastOrNull()) {
                                null -> err(lineNumber, "cannot start document with closure")
                                is FunctionCall -> { }
                                is Word -> tokens.push((tokens.pop() as Word).amendToFn())
                                else -> err(lineNumber, "token \"${tokens.last()}\" cannot precede closure")
                            }
                        }
                        tokens.push(StartClosure(lineNumber))
                    }
                    '}' -> tokens.push(EndClosure(lineNumber))
                    '\n' -> {
                        pushNewLnToken()
                        lineNumber++
                    }
                    else -> {
                        if (!c.isWhitespace()) {
                            sb.append(c)
                        } else {
                            // whitespace means the previous word just ended -- generate token
                            pushWordToken()
                        }
                    }
                }
                // previous char was a backslash
                LexerContext.ESCAPE_OR_LNJOIN -> {
                    processEscapedChar(c)
                    contextStack.pop()
                }
            }

            i = br.read()
        }
        return tokens
    }
}
