package beethoven.Generation.VerilogStub

import beethoven.AcceleratorBlackBoxCore.VerilogPort
import os.Path

/** Orchestrates the user-facing `<Name>.v` blackbox stub: writes a fresh
  * one when absent, or rewrites just the port list (preserving the body)
  * when present.
  *
  * Strategy:
  *   1. File missing -> write a fresh marker-bracketed stub.
  *   2. File present + has BEGIN/END markers -> splice between them.
  *   3. File present + no markers -> structural parse to find the port
  *      list parens, replace with marker-bracketed interior. The file
  *      gains markers as a side effect; subsequent runs use path (2).
  *   4. Structural parse fails -> surface a clear error directing the
  *      user to add markers manually.
  *
  * Each strategy is a method on this object; the public entry point is
  * `syncCore`.
  */
object VerilogStubManager {

  /** Outcome of a sync attempt, suitable for log output. */
  sealed trait SyncOutcome { def message: String }
  case class WroteFreshStub(path: Path) extends SyncOutcome {
    def message = s"Wrote fresh stub at '$path'"
  }
  case class UpdatedPorts(path: Path) extends SyncOutcome {
    def message = s"Updated ports in '$path'"
  }
  case class NoChange(path: Path) extends SyncOutcome {
    def message = s"Ports already up to date in '$path'"
  }
  case class CouldNotLocate(path: Path, reason: String) extends SyncOutcome {
    def message =
      s"Could not auto-sync ports in '$path': $reason. " +
        s"To enable auto-sync, wrap the port list with " +
        s"'${VerilogPortRenderer.BeginMarkerLine}' and " +
        s"'${VerilogPortRenderer.EndMarkerLine}'."
  }

  def syncCore(
      bbPath: Path,
      moduleName: String,
      bbMacroParams: String,
      ports: Iterable[VerilogPort]
  ): SyncOutcome = {
    if (!os.exists(bbPath)) writeFreshStub(bbPath, moduleName, bbMacroParams, ports)
    else updateExisting(bbPath, moduleName, ports)
  }

  // --- private strategies ------------------------------------------

  private def writeFreshStub(
      bbPath: Path,
      moduleName: String,
      bbMacroParams: String,
      ports: Iterable[VerilogPort]
  ): SyncOutcome = {
    val freshContent = VerilogPortRenderer.renderFreshStub(moduleName, bbMacroParams, ports)
    os.write(bbPath, freshContent)
    WroteFreshStub(bbPath)
  }

  private def updateExisting(
      bbPath: Path,
      moduleName: String,
      ports: Iterable[VerilogPort]
  ): SyncOutcome = {
    val source = os.read(bbPath)
    spliceBetweenMarkers(source, ports) match {
      case Some(updated) => writeIfChanged(bbPath, source, updated)
      case None          => spliceStructurally(bbPath, source, moduleName, ports)
    }
  }

  /** Marker-based splice. Returns `None` if the file lacks both
    * markers (caller falls back to structural parse). Returns `Some` —
    * possibly the source unchanged — when both markers are present.
    *
    * If exactly one marker is present, we treat it as user error and
    * surface `Some(source)` unchanged so the file isn't silently
    * mutated; the structural fallback won't run, and the user sees
    * "no change" rather than a mysterious rewrite.
    */
  private def spliceBetweenMarkers(
      source: String,
      ports: Iterable[VerilogPort]
  ): Option[String] = {
    val lines = source.split("\n", -1)
    val beginIdx = lines.indexWhere(_.contains(VerilogPortRenderer.BeginToken))
    val endIdx = lines.indexWhere(_.contains(VerilogPortRenderer.EndToken))
    if (beginIdx < 0 && endIdx < 0) return None
    if (beginIdx < 0 || endIdx < 0 || endIdx <= beginIdx) {
      // Markers present but malformed; defer to structural fallback by
      // returning None, so the user gets the actionable error message
      // rather than us silently doing the wrong thing.
      return None
    }
    val replacement = VerilogPortRenderer.renderInteriorLines(ports)
    val updated =
      lines.slice(0, beginIdx + 1) ++
        replacement ++
        lines.slice(endIdx, lines.length)
    Some(updated.mkString("\n"))
  }

  private def spliceStructurally(
      bbPath: Path,
      source: String,
      moduleName: String,
      ports: Iterable[VerilogPort]
  ): SyncOutcome = {
    VerilogPortRegion.locate(source, moduleName) match {
      case Right(span) =>
        val updated = span.replace(source, VerilogPortRenderer.renderMarkerBracketedInterior(ports))
        writeIfChanged(bbPath, source, updated)
      case Left(err) =>
        CouldNotLocate(bbPath, err.message)
    }
  }

  private def writeIfChanged(bbPath: Path, oldSrc: String, newSrc: String): SyncOutcome = {
    if (oldSrc == newSrc) NoChange(bbPath)
    else {
      os.write.over(bbPath, newSrc)
      UpdatedPorts(bbPath)
    }
  }
}
