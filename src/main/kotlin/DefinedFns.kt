package markup.parser

import java.io.File

private val definedFnCalls: Map<String, (List<Token>, Map<String, Token>) -> FnMapping>
        = mapOf("Math" to ::Math, "math" to ::Math, "Header" to ::Header,
        "Problem" to ::Problem, "Part" to ::Part, "Bold" to ::Bold, "Italic" to ::Italic)

private val aliases: Map<String, String>
        = mapOf("Box" to "mdframed")

fun getFnOrDefault(key: String): (List<Token>, Map<String, Token>) -> FnMapping = definedFnCalls[key]
        ?: { args: List<Token>, kwargs: Map<String, Token> -> DefaultFn(key, args, kwargs) }

abstract class FnMapping(val args: List<Token>, val kwargs: Map<String, Token>, val indentedBody: Boolean) {
    abstract fun begin(ctxStack: Stack<ParseContext>): String
    abstract fun end(): String
    open val bodyCtx: ParseContext = ParseContext.INHERIT_PARENT
}

class DefaultFn(key: String, args: List<Token>, kwargs: Map<String, Token>) : FnMapping(args, kwargs, false) {
    private val name = aliases[key] ?: key.toLowerCase()

    private val nameIsTag = listOf("frac", "mathbb")

    override fun begin(ctxStack: Stack<ParseContext>): String {
        if (name in nameIsTag) {
            val sb = StringBuilder()
            sb.append("\\$name")
            for (a in args) {
                sb.append("{${a.translate()}}")
            }
            return sb.toString()
        }
        return "\\begin{$name}"
    }

    override fun end() = if (name in nameIsTag) "" else "\\end{$name}"
}

class Problem(args: List<Token>, kwargs: Map<String, Token>) : FnMapping(args, kwargs, true) {
    override fun begin(ctxStack: Stack<ParseContext>): String {
        val s = "\\subsection*{$problemNum. ${kwargs["name"]?.translate()}} \\begin{enumerate}"
        problemNum++
        return s
    }
    override fun end() = "\\end{enumerate} \\clearpage"

    companion object {
        var problemNum = 0
    }
}

class Part(args: List<Token>, kwargs: Map<String, Token>) : FnMapping(args, kwargs, true) {
    override fun begin(ctxStack: Stack<ParseContext>) = "\\item ${kwargs["name"]?.translate()} \\\\"
    override fun end() = ""
}

class Bold(args: List<Token>, kwargs: Map<String, Token>) : FnMapping(args, kwargs, false) {
    var math = false
    override fun begin(ctxStack: Stack<ParseContext>): String {
        math = ctxStack.last() == ParseContext.MATH
        return "\\" + (if (math) "mathbf" else "textbf") + "{"
    }
    override fun end(): String {
        return "}"
    }
}

class Italic(args: List<Token>, kwargs: Map<String, Token>) : FnMapping(args, kwargs, false) {
    override fun begin(ctxStack: Stack<ParseContext>): String {
        return "\\textit{"
    }
    override fun end(): String {
        return "}"
    }
}

// TODO abstract out later
class Header(args: List<Token>, kwargs: Map<String, Token>) : FnMapping(listOf(), mapOf(), false) {
    val configLines = File("tempConfig.txt").readLines()
    val hwName = configLines[0]
    val name = configLines[1]
    val sid = configLines[2]
    val className = configLines[3]
    val sem = configLines[4]
    val instr = configLines[5]

    override fun begin(ctxStack: Stack<ParseContext>) =
"""
\documentclass{article}
\usepackage{amsmath,amssymb,amsthm,tikz,tkz-graph,color,chngpage,soul,hyperref,csquotes,graphicx,floatrow, yfonts}
\newcommand*{\QEDB}{\hfill\ensuremath{\square}}\newtheorem*{prop}{Proposition}
\renewcommand{\theenumi}{\alph{enumi}}\usepackage[shortlabels]{enumitem}
\usepackage[nobreak=true]{mdframed}\usetikzlibrary{matrix,calc, automata, positioning}
\MakeOuterQuote{"}\usepackage[margin=1in]{geometry} \newtheorem{theorem}{Theorem}
\usepackage{tabto}
\NumTabs{20}
\usepackage{fancyhdr}
\usepackage{pdfpages}
\pagestyle{fancy}
\hypersetup{colorlinks=true, urlcolor=blue}
\headheight=40pt
\renewcommand{\headrulewidth}{6pt}
\newcommand{\lt}{<}
\newcommand{\gt}{>}
\rfoot{$name | $sid}
\lhead{\Large\fontfamily{lmdh}\selectfont $className \\$sem \tab\tab $instr}
\rhead{\LARGE \fontfamily{lmdh}\selectfont $hwName}
"""

    override fun end() = ""

    override val bodyCtx = ParseContext.INHERIT_PARENT
}

class Math(args: List<Token>, kwargs: Map<String, Token>) : FnMapping(args, kwargs, true) {
    override fun begin(ctxStack: Stack<ParseContext>) = "\\begin{align}"

    override fun end() = "\\end{align}"

    override val bodyCtx = ParseContext.MATH
}


