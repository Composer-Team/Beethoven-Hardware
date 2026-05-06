package beethoven.Generation.VerilogStub

import scala.util.matching.Regex

/** Locates the port-list `( ... )` for `module <Name>` inside an existing
  * Verilog source string. The returned span lets callers replace just the
  * interior of the parens, leaving everything else (parameters, body,
  * `endmodule`) untouched.
  *
  * Decoupled from rendering and file IO so it can be unit-tested
  * against arbitrary source snippets without touching disk.
  */
private[VerilogStub] object VerilogPortRegion {

  /** Inclusive offsets of the opening `(` and closing `)` of the port
    * list. `replace` returns a new source string with the interior of
    * the parens swapped for `newInterior`; the parens themselves stay.
    */
  case class PortListSpan(openParenPos: Int, closeParenPos: Int) {
    def replace(source: String, newInterior: String): String =
      source.substring(0, openParenPos + 1) +
        newInterior +
        source.substring(closeParenPos)
  }

  sealed trait LocateError {
    def message: String
  }
  case class ModuleHeaderNotFound(moduleName: String) extends LocateError {
    val message = s"could not find 'module $moduleName' declaration"
  }
  case class UnbalancedParens(detail: String) extends LocateError {
    val message = s"could not balance parens in module header: $detail"
  }
  case class UnexpectedToken(detail: String) extends LocateError {
    val message = s"unexpected source structure in module header: $detail"
  }

  def locate(
      source: String,
      moduleName: String
  ): Either[LocateError, PortListSpan] = {
    findModuleNameEnd(source, moduleName) match {
      case None => Left(ModuleHeaderNotFound(moduleName))
      case Some(afterName) =>
        skipParameterSection(source, afterName).flatMap { afterParams =>
          locatePortListParens(source, afterParams)
        }
    }
  }

  // --- private locators --------------------------------------------

  private def findModuleNameEnd(source: String, moduleName: String): Option[Int] = {
    val pat: Regex = ("\\bmodule\\s+" + Regex.quote(moduleName) + "\\b").r
    pat.findFirstMatchIn(source).map(_.end)
  }

  /** If there's a `#( ... )` parameter section right after the module
    * name, advance past it. Otherwise return the trivia-skipped position
    * unchanged.
    */
  private def skipParameterSection(source: String, from: Int): Either[LocateError, Int] = {
    val pos = VerilogLexer.skipTrivia(source, from)
    if (pos >= source.length)
      return Left(UnexpectedToken("source ends after module name"))
    if (source.charAt(pos) != '#') return Right(pos)

    val afterHash = VerilogLexer.skipTrivia(source, pos + 1)
    if (afterHash >= source.length || source.charAt(afterHash) != '(')
      return Left(UnexpectedToken("expected '(' after '#' in parameter section"))
    VerilogLexer.findMatchingParen(source, afterHash) match {
      case Left(err)        => Left(UnbalancedParens(err))
      case Right(closeParm) => Right(VerilogLexer.skipTrivia(source, closeParm + 1))
    }
  }

  /** Expect the next non-trivia character to be `(`; return the span
    * from that paren to its match.
    */
  private def locatePortListParens(source: String, from: Int): Either[LocateError, PortListSpan] = {
    if (from >= source.length || source.charAt(from) != '(')
      return Left(UnexpectedToken("expected '(' opening port list"))
    VerilogLexer.findMatchingParen(source, from) match {
      case Left(err)        => Left(UnbalancedParens(err))
      case Right(closePos)  => Right(PortListSpan(from, closePos))
    }
  }
}
