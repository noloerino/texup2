package markup.lexer

import markup.parser.*
import java.io.Reader

enum class LexerContext {
    NORMAL, // Nothing special
    FN_CALL, // Between the parentheses of a function
    FN_QUOTED_ARG, // Inside quote marks inside a function call
    FN_LIST_ARG, // Inside [ in a function call
    FN_OBJ_ARG, // Inside { in a function call (basically a json obj)
    ESCAPE_OR_LNJOIN, // After a backslash, will either escape the next character or cause a line join
    COMMENT
}

// Stack extension functions
fun <T> MutableList<T>.pop(): T = removeAt(size - 1)
fun <T> MutableList<T>.push(e: T) = add(e)

class Lexer(private val rdr: Reader) {
    private var i: Int = rdr.read()
    private var tokens = mutableListOf<Token>()
    private var lineNumber = 1
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
        lineNumber++
    }

    private fun onCommentChar() {
        pushWordToken()
        contextStack.push(LexerContext.COMMENT)
    }

    private fun onOpenCurl() {
        when (tokens.last()) {
            /* There are three possible interpretations of the curly brace:
             * 1) The start of some kind of dictionary.
             * 2) The start of a closure following a function call.
             * 3) Just a normal old LaTeX call.
             * The distinction can be made by the previous token: if it was a Word, then it's probably just a vanilla
             * LaTeX command; if it's an EndFnCall, then it's a function; if it was an =, then we can assume the user
             * meant to declare a dictionary. Any other preceding token is a little nonsensical, and can fairly
             * safely be ignored until erroring at parse time.
             */
            is EndFnCall, is Word -> onOpenClosure()
            else -> {
                tokens.push(StartObj(lineNumber))
                contextStack.push(LexerContext.FN_OBJ_ARG)
            }
        }
    }

    private fun err(lineNumber: Int, msg: String): Nothing = throw Exception("Error during lexing: $msg (line $lineNumber)")

    // called as soon as an open paren is hit; only processes for normal function call state
    private fun processFnCall(c: Char) {
        when (c) {
            '%' -> onCommentChar()
            '\"' -> contextStack.push(LexerContext.FN_QUOTED_ARG)
            '[' -> {
                tokens.push(ListArgOpen(lineNumber))
                contextStack.push(LexerContext.FN_LIST_ARG)
            }
            '{' -> onOpenCurl()
            '=', ',' -> onFnDelimChar(c)
            ')' -> {
                tokens.push(EndFnCall(lineNumber))
                contextStack.pop()
            }
            '\n' -> pushNewLnToken()
            else -> if (c.isWhitespace()) pushWordToken() else sb.append(c)
        }
    }

    // Describes universal behavior upon encountering a delimiter within a function call
    private fun onFnDelimChar(symbol: Char) {
        val constr = when (symbol) {
            '=' -> ::KwargAssn
            ',' -> ::ArgDelim
            ':' -> ::KVDelim
            else -> err(lineNumber, "Invalid delimiter within function \"$symbol\"")
        }
        pushWordToken()
        tokens.push(constr(lineNumber))
    }

    // should be called when the context is escape or lnjoin
    private fun processEscapedChar(c: Char) {
        when (c) {
            // line join
            '\\' -> {
                pushWordToken()
                tokens.push(LnJoin(lineNumber))
            }
            // 'n' -> pushNewLnToken() interferes with some control sequences
            // unsure quite what to do here
            '\"' -> if (context == LexerContext.FN_QUOTED_ARG) sb.append(c) else sb.append(c)
            '%', '$' -> Word(lineNumber, "\\$c")
            '{', '}' -> Word(lineNumber, c.toString())
            '\n' -> {
                pushWordToken()
                lineNumber++
            } //pushNewLnToken()
            else -> sb.append("\\$c") // just a normal LaTeX control sequence
        }
    }

    private fun onOpenClosure() {
        contextStack.push(LexerContext.NORMAL)
        tokens.push(StartClosure(lineNumber))
    }

    fun lex(): List<Token> {
        while (i != -1) {
            val c = i.toChar()
            when (context) {
                LexerContext.COMMENT -> if (c == '\n') {
                    tokens.push(Comment(lineNumber, sb.toString()))
                    clearSB()
                    pushNewLnToken()
                    contextStack.pop()
                } else {
                    sb.append(c)
                }
                LexerContext.FN_LIST_ARG -> when (c) {
                    '%' -> onCommentChar()
                    ',' -> onFnDelimChar(c)
                    '{' -> onOpenCurl()
                    '\"' -> contextStack.push(LexerContext.FN_QUOTED_ARG)
                    '[' -> { // nested lists!
                        tokens.push(ListArgOpen(lineNumber))
                        contextStack.push(LexerContext.FN_LIST_ARG)
                    }
                    ']' -> { // exit state
                        tokens.push(ListArgClose(lineNumber))
                        contextStack.pop()
                    }
                    else -> if (c.isWhitespace()) pushWordToken() else sb.append(c)
                }
                LexerContext.FN_QUOTED_ARG -> when (c) {
                    '%' -> onCommentChar()
                    '\\' -> contextStack.push(LexerContext.ESCAPE_OR_LNJOIN)
                    '\"' -> { // exit state
                        tokens.push(QuotedString(lineNumber, sb.toString()))
                        clearSB()
                        contextStack.pop()
                    }
                    else -> sb.append(c)
                }
                LexerContext.FN_OBJ_ARG -> when (c) {
                    '%' -> onCommentChar()
                    ':', ',' -> onFnDelimChar(c)
                    '\\' -> contextStack.push(LexerContext.ESCAPE_OR_LNJOIN)
                    '\"' -> contextStack.push(LexerContext.FN_QUOTED_ARG)
                    '[' -> contextStack.push(LexerContext.FN_LIST_ARG)
                    '{' -> onOpenCurl()
                    '}' -> { // exit state
                        tokens.push(EndObj(lineNumber))
                        contextStack.pop()
                    }
                    else -> if (c.isWhitespace()) pushWordToken() else sb.append(c)
                }
                LexerContext.NORMAL -> when (c) {
                    '\\' -> contextStack.push(LexerContext.ESCAPE_OR_LNJOIN)
                    // exiting the functioncall state is handled by a separate method
                    '(' -> {
                        if (!sb.isEmpty()) {
                            // looks like "FunctionCall("
                            // we're starting a function call
                            contextStack.push(LexerContext.FN_CALL)
                            tokens.push(FunctionName(lineNumber, sb.toString()))
                            clearSB()
                            tokens.push(StartFnCall(lineNumber))
                        } else {
                            // looks like "FunctionCall (", which we define not to be a function call
                            // nothing to see here, move along
                            sb.append('(')
                        }
                    }
                    '%' -> onCommentChar()
                    '$' -> {  // Math environment
                        pushWordToken()
                        tokens.push(MathDelim(lineNumber))
                    }
                    '{' -> onOpenClosure()
                    '}' -> {
                        contextStack.pop()
                        tokens.push(EndClosure(lineNumber))
                    }
                    '\n' -> pushNewLnToken()
                    else -> {
                        if (!c.isWhitespace()) {
                            // Special case for math delimiters to check $ vs $$
                            if (c == '$' && tokens.last() is MathDelim) {
                                (tokens.last() as MathDelim).doubleDollar = true
                            } else {
                                sb.append(c)
                            }
                        } else {
                            // whitespace means the previous word just ended -- generate token
                            pushWordToken()
                        }
                    }
                }
                LexerContext.FN_CALL -> processFnCall(c)
                // previous char was a backslash
                LexerContext.ESCAPE_OR_LNJOIN -> {
                    processEscapedChar(c)
                    contextStack.pop()
                }
            }

            i = rdr.read()
        }
        return tokens
    }
}
