package markup.parser

typealias Stack<T> = java.util.LinkedList<T>

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

/**
 * A token outputted by the lexer.
 */
abstract class Token {

    open val eatsTrailingNewLn = false

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

    protected fun err(msg: String): Nothing = throw Exception("Error during parsing: $msg (line $lineNumber)")
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
 */
// @param args unnamed parameters passed to the function
// @param kwargs nammed parameters passed to the function
// upon further deliberation, it is safer to handle args and kwargs later
data class FunctionName(override val lineNumber: Int, val fnName: String) : Token() {

    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {
        when (fnName) {

        }
        // should be defined elsewhere
        return
    }

    override fun repr() = "Function$fnName"
}

// ----- TOKENS WITHIN FUNCTION CALL CONTEXT -----

data class QuotedString(override val lineNumber: Int, val content: String) : Token() {

    override fun translate(context: Stack<ParseContext>, output: MutableList<String>) {

    }

    override fun repr() = "\"" + content + "\""
}

// ,
data class ArgDelim(override val lineNumber: Int) : MetaToken(',')

// =
data class KwargAssn(override val lineNumber: Int) : MetaToken('=')

// :
data class KVDelim(override val lineNumber: Int) : MetaToken(':')

// [
data class ListArgOpen(override val lineNumber: Int) : MetaToken('[')

// ]
data class ListArgClose(override val lineNumber: Int) : MetaToken(']')

// ----- -----

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
}

// }
data class EndClosure(override val lineNumber: Int) : MetaToken('}') {
    override val eatsTrailingNewLn = true
}