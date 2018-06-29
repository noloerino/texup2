package markup.parser

import java.util.NoSuchElementException

typealias Stack<T> = MutableList<T>
fun <T> MutableList<T>.pop(): T = removeAt(size - 1)
fun <T> MutableList<T>.push(e: T) = add(e)

/**
 * The context in which a token is to be parsed. Should be stored in a stack somewhere,
 * so a context's parent can be found easily.
 *
 * @param substitutions whether or not substitutions are made in the body of this token.
 */
enum class ParseContext(val substitutions: Boolean) {
    NORMAL(true),
    MATH(true), // Math input mode, triggered either by $$, $, or Math {}
    RAW(false), // Text that should not be modified in any way (including \\ for newlines)
    BLOCK_COMMENT(false), // Block comments, like /* */
    // FN_PARSE_ARG(true), // Between the parentheses of a function call, parsing arguments
    // FN_OBJ_ARG(false), // a JSON-like object passed into a type
    // FN_QUOTED_ARG(false), // Parsing between quote marks of a string arumgnet
    // FN_LIST_ARG(true), // Parsing a list provided as an argument to a function
}

class Parser(fromLexer: List<Token>) {
    val tokens: List<Token>

    init {
        val _tokens = mutableListOf<Token>()
        val iter = fromLexer.iterator()
        while (iter.hasNext()) {
            val t = iter.next()
            if (t is FunctionName) {
                try {
                    _tokens.push(buildFnCall(t, iter))
                } catch (e: NoSuchElementException) {
                    if (!iter.hasNext()) {
                        t.err("error in lexing (no tokens following fn call ${t.fnName})")
                    }
                }
            } else {
                _tokens.push(t)
            }
        }
        // validate
        _tokens.filter { it.intermediate }
                .forEach { it.err("Unparsed intermediate token ${it.repr()}") }
        tokens = _tokens
    }

    fun parse(): List<String> {

        return listOf()
    }
}

/**
 * A token outputted by the lexer.
 */
abstract class Token {

    open val eatsTrailingNewLn = false
    /**
     * A token is "intermediate" if it needs to be processed before it has any actual meaning.
     */
    open val intermediate = false

    abstract val lineNumber: Int

    /**
     * Outputs the translation of this token into LaTeX.
     *
     * @param context The current context.
     * @param output The queue of strings representing the document in LaTeX. Each element is a line in the
     * resulting output file.
     */
    abstract fun translate(context: Stack<ParseContext>, output: MutableList<String>)

    /**
     * Produces a debug string. Strictly speaking, it's not actually like Pythong's repr function,
     * but it's nice to have around.
     */
    abstract fun repr(): String

    fun err(msg: String): Nothing = throw Exception("Error during parsing: $msg (line $lineNumber)")
}

/**
 * Represents a "function," which looks like "FunctionName(args, kwarg='value'".
 * The lexer will make the distinction between "FunctionName(...)" and "FunctionName (...)"
 * (the latter with an added space before the open paren, as LaTeX is not whitespace sensitive.
 *
 * Furthermore, any name preceding an unescaped curly brace (i.e. "FunctionName { ... }")
 * will be treated as a function call with no arguments.
 *
 * @param fnName the name of the function being called
 * @param args unnamed parameters passed to the function
 * @param kwargs named parameters passed to the function
 */
data class FunctionCall(override val lineNumber: Int, val fnName: String,
                        val args: List<Token>, val kwargs: Map<String, Token>) : Token() {

    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {
        when (fnName) {

        }
    }

    override fun repr() = "FnCall($fnName)"
}

private fun buildFnCall(nameObj: FunctionName, iter: Iterator<Token>): FunctionCall {
    val args = mutableListOf<Token>()
    val kwargs = mutableMapOf<String, Token>()
    require(iter.next() is StartFnCall) { "Error in lexing: function name must be followed by open paren" }
    read@ while (true) {
        val t1 = buildFnArg(iter.next(), iter)
        val maybeEq = iter.next()
        when (maybeEq) {
            is ArgDelim -> args.push(t1) // comma right after single value
            is KwargAssn -> {
                // since we hit an =, t1 is a key
                val key = validateKeyType(t1)
                val value = buildFnArg(iter.next(), iter)
                kwargs[key] = value
                val maybeComma = iter.next()
                when (maybeComma) {
                    is ArgDelim -> {}
                    is EndFnCall -> break@read
                    else -> maybeComma.err("expected delimiter, got ${maybeComma.repr()}")
                }
            }
            is EndFnCall -> {
                args.push(t1)
                break@read
            }
            else -> maybeEq.err("expected delimiter, got ${maybeEq.repr()}")
        }
    }
    return FunctionCall(nameObj.lineNumber, nameObj.fnName, args, kwargs)
}

data class FnArgObj(override val lineNumber: Int, val dict: Map<String, Token>) : Token() {
    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {

    }
    override fun repr() = dict.toString()
}

data class FnArgLst(override val lineNumber: Int, val items: List<Token>) : Token() {
    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {

    }
    override fun repr() = items.toString()
}

private fun buildFnArg(signal: Token, iter: Iterator<Token>): Token {
    return when (signal) {
        is StartObj -> buildFnArgObj(signal.lineNumber, iter)
        is ListArgOpen -> buildFnArgLst(signal.lineNumber, iter)
        is Word, is QuotedString -> signal
        is FunctionName -> buildFnCall(signal, iter)
        else -> signal.err("invalid function arg ${signal.repr()}")
    }
}

private fun validateKeyType(key: Token) = when (key){
        is Word -> key.content
        is QuotedString -> key.content
        else -> key.err("keys must be strings, got ${key.repr()} instead")
    }

private fun buildFnArgObj(lineNumber: Int, iter: Iterator<Token>): FnArgObj {
    val dict = mutableMapOf<String, Token>()
    while (true) {
        val key = iter.next()
        val strKey = validateKeyType(key)
        val nxt = iter.next()
        if (nxt !is KVDelim) {
            FnArgObj(lineNumber, dict).err("key/value pairs must be separated by ':', got ${nxt.repr()} instead")
        }
        val value = iter.next()
        dict[strKey] = buildFnArg(value, iter)
        val sep = iter.next()
        if (sep is EndObj) {
            break
        } else if (sep !is ArgDelim) {
            FnArgObj(lineNumber, dict).err("members of dict must be separated by ',', got ${sep.repr()} instead")
        }
    }
    return FnArgObj(lineNumber, dict)
}

private fun buildFnArgLst(lineNumber: Int, iter: Iterator<Token>): FnArgLst {
    val lst = mutableListOf<Token>()
    while (true) {
        lst.add(buildFnArg(iter.next(), iter))
        val sep = iter.next()
        if (sep is ListArgClose) {
            break
        } else if (sep !is ArgDelim) {
            FnArgLst(lineNumber, lst).err("members of list must be separated by ',', got ${sep.repr()} instead")
        }
    }
    return FnArgLst(lineNumber, lst)
}

data class FunctionName(override val lineNumber: Int, val fnName: String) : Token() {

    override val intermediate = true

    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {
        return
    }

    override fun repr() = "Fn$fnName"
}

data class QuotedString(override val lineNumber: Int, val content: String) : Token() {

    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {

    }

    override fun repr() = "\"" + content + "\""
}

data class Word(override val lineNumber: Int, val content: String) : Token() {

    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {
        output.add(content)
    }

    // If a closure is attached, this automatically becomes a function instead
    fun amendToFn() = FunctionName(lineNumber, content)

    override fun repr() = content
}

data class Comment(override val lineNumber: Int, val content: String) : Token() {
    override val eatsTrailingNewLn = true
    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {
        output.add("%$content")
    }

    override fun repr() = "%(...)"
}

// ------ Special chars ------

data class MathDelim(override val lineNumber: Int) : Token() {
    // Determines if $ or $$ is generated
    var doubleDollar = false

    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {
        output.add(if (doubleDollar) "$$" else "$")
    }

    override fun repr() = if (doubleDollar) "$$" else "$"
}

data class NewLn(override val lineNumber: Int, val prev: Token?) : Token() {

    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {
        if (prev == null || !prev.eatsTrailingNewLn) {
            output.add("\\\\") // that's 2 backslashes
        }
        output.add("\n") // for readability; does not affect LaTeX in compilation
    }

    override fun repr() = "\n"
}

/**
 * A dummy token that doesn't appear in translation, usually used to represent the start
 * of some kind of scoping thing
 */
abstract class MetaToken(val specialChar: Char) : Token() {
    final override fun translate(context: Stack<ParseContext>, output: MutableList<String>) { }
    override fun repr() = "$specialChar"
}

data class LnJoin(override val lineNumber: Int) : MetaToken('\\') {
    override val eatsTrailingNewLn = true
}

// (
data class StartFnCall(override val lineNumber: Int) : MetaToken('(')

// )
data class EndFnCall(override val lineNumber: Int) : MetaToken(')')

// {
data class StartClosure(override val lineNumber: Int) : MetaToken('{') {
    override val eatsTrailingNewLn = true
    override fun repr() = "StartClo:"
}

// }
data class EndClosure(override val lineNumber: Int) : MetaToken('}') {
    override val eatsTrailingNewLn = true
    override fun repr() = ":EndClo"
}

data class StartObj(override val lineNumber: Int) : MetaToken('{') {
    override val eatsTrailingNewLn = true
    override fun repr() = "StartObj:"
}

data class EndObj(override val lineNumber: Int) : MetaToken('}') {
    override val eatsTrailingNewLn = true
    override fun repr() = ":EndObj"
}

/**
 * A special token that is defined only in the context of a function call. These are only intermediates, and
 * will be combined with a FunctionName token to generate a FunctionCall token.
 */

abstract class FnMedToken(specialChar: Char) : MetaToken(specialChar) {
    override val intermediate = true
}

// ,
data class ArgDelim(override val lineNumber: Int) : FnMedToken(',')

// =
data class KwargAssn(override val lineNumber: Int) : FnMedToken('=')

// :
data class KVDelim(override val lineNumber: Int) : FnMedToken(':')

// [
data class ListArgOpen(override val lineNumber: Int) : FnMedToken('[')

// ]
data class ListArgClose(override val lineNumber: Int) : FnMedToken(']')

// ----- -----