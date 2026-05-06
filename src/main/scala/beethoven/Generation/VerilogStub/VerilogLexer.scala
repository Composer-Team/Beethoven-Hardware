package beethoven.Generation.VerilogStub

/** Tiny lexer for Verilog source — just enough to skip "trivia" (comments,
  * strings, attribute instances) and balance parentheses. The port-list
  * locator builds on top of this; nothing else in the framework uses it.
  *
  * Intentionally NOT a full Verilog parser. Limits acknowledged in the
  * docs: preprocessor macros that affect structural punctuation, escaped
  * identifiers (`\foo$bar `), and `\`ifdef` inside the module header are
  * not handled. Files that hit those constructs fall through to the
  * marker-bracketed splice path.
  */
private[VerilogStub] object VerilogLexer {

  /** Advance past whitespace, line/block comments, string literals, and
    * Verilog-2001 attribute instances starting at `from`. Returns the
    * next index pointing at a non-trivia character, or `source.length`
    * if the rest of the file is all trivia.
    */
  def skipTrivia(source: String, from: Int): Int = {
    var i = from
    while (i < source.length) {
      val c = source.charAt(i)
      if (c.isWhitespace) {
        i += 1
      } else if (startsLineComment(source, i)) {
        i = endOfLineComment(source, i)
      } else if (startsBlockComment(source, i)) {
        i = endOfBlockComment(source, i)
      } else if (startsAttribute(source, i)) {
        i = endOfAttribute(source, i)
      } else {
        return i
      }
    }
    i
  }

  /** Find the index of the `)` that closes the `(` at `openPos`.
    * Counts nested parens and ignores parens inside comments, string
    * literals, and attribute instances. Returns `Left` if the source
    * ends before the paren is closed.
    */
  def findMatchingParen(source: String, openPos: Int): Either[String, Int] = {
    require(
      openPos >= 0 && openPos < source.length && source.charAt(openPos) == '(',
      s"openPos=$openPos must point to '('"
    )
    var depth = 1
    var i = openPos + 1
    while (i < source.length && depth > 0) {
      val c = source.charAt(i)
      if (startsLineComment(source, i)) {
        i = endOfLineComment(source, i)
      } else if (startsBlockComment(source, i)) {
        i = endOfBlockComment(source, i)
      } else if (startsAttribute(source, i)) {
        i = endOfAttribute(source, i)
      } else if (c == '"') {
        i = endOfString(source, i)
      } else if (c == '(') {
        depth += 1; i += 1
      } else if (c == ')') {
        depth -= 1
        if (depth == 0) return Right(i)
        i += 1
      } else {
        i += 1
      }
    }
    Left(s"unmatched '(' at offset $openPos")
  }

  // --- private trivia recognizers ---------------------------------

  private def startsLineComment(s: String, i: Int): Boolean =
    i + 1 < s.length && s.charAt(i) == '/' && s.charAt(i + 1) == '/'

  private def startsBlockComment(s: String, i: Int): Boolean =
    i + 1 < s.length && s.charAt(i) == '/' && s.charAt(i + 1) == '*'

  /** Verilog-2001 attribute instance opens with `(*` (not the literal
    * paren we use for grouping). Distinguishing requires a 2-char peek.
    */
  private def startsAttribute(s: String, i: Int): Boolean =
    i + 1 < s.length && s.charAt(i) == '(' && s.charAt(i + 1) == '*'

  private def endOfLineComment(s: String, from: Int): Int = {
    var i = from + 2
    while (i < s.length && s.charAt(i) != '\n') i += 1
    i
  }

  private def endOfBlockComment(s: String, from: Int): Int = {
    var i = from + 2
    while (i + 1 < s.length && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i += 1
    if (i + 1 < s.length) i + 2 else s.length
  }

  private def endOfAttribute(s: String, from: Int): Int = {
    var i = from + 2
    while (i + 1 < s.length && !(s.charAt(i) == '*' && s.charAt(i + 1) == ')')) i += 1
    if (i + 1 < s.length) i + 2 else s.length
  }

  /** Skip a string literal, honoring `\\` escapes. `from` must point at
    * the opening `"`.
    */
  private def endOfString(s: String, from: Int): Int = {
    var i = from + 1
    while (i < s.length && s.charAt(i) != '"') {
      if (s.charAt(i) == '\\' && i + 1 < s.length) i += 2
      else i += 1
    }
    if (i < s.length) i + 1 else i
  }
}
