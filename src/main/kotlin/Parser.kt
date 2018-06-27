package markup.parser

/**
 * The context in which a token is to be parsed. Should be stored in a stack somewhere,
 * so a context's parent can be found easily.
 *
 * @param substitutions whether or not substitutions are made in the body of this token.
 */
enum class _ParseContext(val substitutions: Boolean) {
    NORMAL(true),
    MATH(true), // Math input mode, triggered either by $$, $, or Math {}
    RAW(false), // Text that should not be modified in any way (including \\ for newlines)
    BLOCK_COMMENT(false), // Block comments, like /* */
    FN_PARSE_ARG(true), // Between the parentheses of a function call, parsing arguments
    // FN_QUOTED_ARG(false), // Parsing between quote marks of a string arumgnet
    // FN_LIST_ARG(true) // Parsing a list provided as an argument to a function
}

data class ParseContext(val context: _ParseContext, val parent: ParseContext)

/**
 * A token outputted by the lexer.
 */
abstract class Token {

    abstract val lineNumber: Int

    open val stripsTrailingNewline = true // if true, newline characters at end of raw are removed

    /**
     * Outputs the translation of this token into LaTeX.
     *
     * @param context The current context.
     * @param output The queue of strings represeting the document in LaTeX. Each element is a line in the
     * resulting output file.
     */
    abstract fun translate(context: ParseContext, output: List<String>)

    protected fun err(msg: String) = "$msg (line $lineNumber)"
}

/**
 * Represents a "function," which looks like "FunctionName(args, kwarg='value'".
 * The lexer will make the distinction between "FunctionName(...)" and "FunctionName (...)"
 * (the latter with an added space before the open paren, as LaTeX is not whitespace sensitive.
 *
 * Furthermore, any name preceding an unescaped curly brace (i.e. "FunctionName { ... }")
 * will be treated as a function call with no arguments.
 *
 * This is one of the few token classes that parses recursively, as the body, args, and kwargs parameters
 * can all contain other tokens.
 */
data class FunctionCall(override val lineNumber: Int, val body: List<Token>,
                        val args: List<Token>, val kwargs: Map<String, Token>) : Token() {

    override fun translate(context: ParseContext, output: List<String>) {
        return
    }
}

data class QuotedString(override val lineNumber: Int, val content: String) : Token() {

    override fun translate(context: ParseContext, output: List<String>) {

    }
}

data class ListArg(override val lineNumber: Int, val items: List<Token>) : Token() {

    override fun translate(context: ParseContext, output: List<String>) {

    }
}
