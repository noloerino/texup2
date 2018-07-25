package markup.parser

import java.util.NoSuchElementException

typealias Stack<T> = MutableList<T>
fun <T> MutableList<T>.pop(): T = removeAt(size - 1)
fun <T> MutableList<T>.push(e: T) = add(e)
fun <T> MutableList<T>.peek(): T = last()

/**
 * The context in which a token is to be parsed. Should be stored in a stack somewhere,
 * so a context's parent can be found easily.
 *
 * @param substitutions whether or not substitutions are made in the body of this token.
 */
enum class ParseContext(val substitutions: Boolean) {
    NORMAL(true),
    MATH(true), // Math input mode, triggered either by $$, $, or Math {}
//    RAW(false), // Text that should not be modified in any way (including \\ for newlines)
//    RAW_MATH(false),
    FN_ARG(true),
    INHERIT_PARENT(true), // Inherits the behavior of the previous thing
    // FN_PARSE_ARG(true), // Between the parentheses of a function call, parsing arguments
    // FN_OBJ_ARG(false), // a JSON-like object passed into a type
    // FN_QUOTED_ARG(false), // Parsing between quote marks of a string arumgnet
    // FN_LIST_ARG(true), // Parsing a list provided as an argument to a function
}

// Stack of parse contexts
private val ctxStack = mutableListOf(ParseContext.NORMAL)
// Stack of function nests
private val fnStack = mutableListOf<FnMapping>()
// The number of indents before the next token
private fun indLevel() = fnStack.size

class Parser(val tokens: List<Token>) {

    fun parse(): List<String> {
        val output = mutableListOf<String>()
        val iter = tokens.iterator()
        while (iter.hasNext()) {
            val t = iter.next()
            val nextToken: Token = if (t is FunctionName) {
                try {
                    buildFnCall(t, iter)
                } catch (e: NoSuchElementException) {
                    if (!iter.hasNext()) {
                        t.err("error in lexing (no tokens following fn call ${t.fnName})")
                    } else {
                        throw e
                    }
                }
            } else {
                t
            }
            output.add(nextToken.translate())
        }
        return output
    }
}

private fun parseClosureToClosure(iter: Iterator<Token>): List<String> {
    // performs parsing between tokens
    // an open closure should have been the last token read out by iter
    // HOWEVER, startclosure's translate method was never called
    // so we need to push this onto the stack
    fnStack.push(lastFnCall!!.fnObj)
    var t = iter.next()
    val output = mutableListOf<String>()
    while (iter.hasNext() && t !is EndClosure) {
        val nextToken = if (t is FunctionName) buildFnCall(t, iter) else t
        output.add(nextToken.translate())
        t = iter.next()
    }
    // add end closure
    output.add(t.translate())
    return output
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
     * these strings is the token that generated it; newline characters are given their own item.
     */
    abstract fun translate(): String

    /**
     * Produces a debug string. Strictly speaking, it's not actually like Pythong's repr function,
     * but it's nice to have around.
     */
    abstract fun repr(): String

    fun err(msg: String): Nothing = throw Exception("Error during parsing: $msg (line $lineNumber)")
    fun warn(msg: String) = System.err.println("Warning: $msg (line $lineNumber)")
}

private var lastFnCall: FunctionCall? = null
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

    override val eatsTrailingNewLn = true

    val fnObj: FnMapping = getFnOrDefault(fnName)(args, kwargs)

    val bodyContext = fnObj.bodyCtx
    fun begin() = fnObj.begin(ctxStack)
    val end = fnObj.end()

    override fun translate(): String {
        if (fnName[0].isLowerCase()) {
            warn("$fnName should be capitalized")
        }
        lastFnCall = this
        // context is not pushed here because there might not be a closure
        return begin()
    }

    override fun repr() = "FnCall($fnName)"
}

private fun buildFnCall(nameObj: FunctionName, iter: Iterator<Token>): FunctionCall {
    val args = mutableListOf<Token>()
    val kwargs = mutableMapOf<String, Token>()
    // eats one token to use up the open paren
    require(iter.next() is StartFnCall) { "Error in lexing: function name must be followed by open paren" }
    read@ while (true) {
        val received = iter.next()
        if (received is Comment || received is NewLn) {
            continue
        } else if (received is EndFnCall) {
            break@read
        }
        val t1 = buildFnArg(received, iter)
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
                    is StartClosure -> flattenWords(parseClosureToClosure(iter))
                    else -> maybeComma.err("${nameObj.fnName} expected delimiter, got ${maybeComma.repr()}")
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

// abstract class FnArg extended by obj, lst, qtdstr

data class FnArgObj(override val lineNumber: Int, val dict: Map<String, Token>) : Token() {
    override fun translate(): String = ""
    override fun repr() = dict.toString()
}

data class FnArgLst(override val lineNumber: Int, val items: List<Token>) : Token() {
    override fun translate(): String = ""
    override fun repr() = items.toString()
}

private fun buildFnArg(signal: Token, iter: Iterator<Token>): Token {
    return when (signal) {
        is StartObj -> buildFnArgObj(signal.lineNumber, iter)
        is ListArgOpen -> buildFnArgLst(signal.lineNumber, iter)
        is Word, is QuotedString -> signal
        is FunctionName -> buildFnCall(signal, iter)
        is EndFnCall -> signal.err("found end paren too early")
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

data class FunctionName(override val lineNumber: Int, val fnName: String) : FnMedToken('f') {
    override fun repr() = "Fn$fnName"
}

data class QuotedString(override val lineNumber: Int, val content: String) : Token() {

    override fun translate() = content

    override fun repr() = "\"" + content + "\""
}

data class Word(override val lineNumber: Int, val content: String) : Token() {

    override fun translate() = content

    override fun repr() = content
}

data class Comment(override val lineNumber: Int, val content: String) : Token() {
    override val eatsTrailingNewLn = true
    override fun translate() = "%$content"
    override fun repr() = "%(...)"
}

// ------ Special chars ------

data class MathDelim(override val lineNumber: Int) : Token() {
    // Determines if $ or $$ is generated
    var doubleDollar = false

    override fun translate(): String {
        if (ctxStack.peek() == ParseContext.MATH) {
            ctxStack.pop()
        } else {
            ctxStack.push(ParseContext.MATH)
        }
        return if (doubleDollar) "$$" else "$"
    }

    override fun repr() = if (doubleDollar) "$$" else "$"
}

data class NewLn(override val lineNumber: Int, val prev: Token?) : Token() {

    override fun translate(): String {
        var s = ""
        if (prev == null || !prev.eatsTrailingNewLn) {
            s += "\\\\" // that's 2 backslashes
        }
        s += "\n" // for readability; does not affect LaTeX in compilation
        return s
    }

    override fun repr() = "\n"
}

/**
 * A dummy token that doesn't appear in translation, usually used to represent the start
 * of some kind of scoping thing
 */
abstract class MetaToken(val specialChar: Char) : Token() {
    override fun translate(): String {
        err("attempted to translate meta token ${repr()}")
    }
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
    override fun translate(): String {
        // begin tags are not handled here because the fn might not need a closure
        fnStack.push(lastFnCall!!.fnObj)
        ctxStack.push(lastFnCall!!.bodyContext)
        return ""
    }
}

// }
data class EndClosure(override val lineNumber: Int) : MetaToken('}') {
    override val eatsTrailingNewLn = true
    override fun repr() = ":EndClo"
    override fun translate(): String {
        ctxStack.pop()
        return fnStack.pop().end()
    }
}

data class StartObj(override val lineNumber: Int) : MetaToken('{') {
    override fun repr() = "StartObj:"
}

data class EndObj(override val lineNumber: Int) : MetaToken('}') {
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