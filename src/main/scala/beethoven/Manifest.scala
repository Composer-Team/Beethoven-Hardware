package beethoven

import org.tomlj.{Toml, TomlParseResult, TomlTable}
import os.Path

import scala.jdk.CollectionConverters._

/** Parsed contents of `Beethoven.toml`. The source of truth for project
  * configuration — no JVM property, env var, or CLI flag overrides any field
  * here.
  *
  * One deliberate exception: **build mode** (simulation vs synthesis) is
  * *not* in the manifest. It's a per-invocation choice, supplied as
  * `--mode <simulation|synthesis>` to [[beethoven.cli.Run]] (or set by the
  * external `beethoven` CLI based on which command was invoked: `sim`,
  * `run`, etc.). This keeps the manifest descriptive ("here's the
  * platform I'm building for") rather than imperative ("build it this
  * way next time").
  *
  * Manifest *location* resolution (CWD vs walk-up vs explicit flag) lives in
  * [[beethoven.cli.Run]] and the external `beethoven` CLI; this module only
  * parses a given path.
  */
final case class Manifest(
    manifestPath: Path,
    project: Manifest.ProjectSection,
    hardware: Manifest.HardwareSection,
    software: Manifest.SoftwareSection,
    platform: Manifest.PlatformSection,
    build: Manifest.BuildSection
) {
  def manifestDir: Path = manifestPath / os.up
}

object Manifest {

  final case class ProjectSection(
      name: String
  )

  final case class HardwareSection(
      srcDir: String,
      beethovenHardwarePath: Option[Path],
      beethovenHardwareVersion: Option[String]
  )

  final case class SoftwareSection(
      srcDir: String
  )

  final case class PlatformSection(
      target: String,
      params: Map[String, Any]
  )

  final case class BuildSection(
      outputDir: String
  )

  /** Parse a specific manifest file. */
  def parse(path: Path): Manifest = {
    if (!os.exists(path)) {
      err(s"Beethoven.toml not found at $path")
    }
    val toml = Toml.parse(path.toNIO)
    if (toml.hasErrors) {
      val errs = toml.errors.asScala.map(_.toString).mkString("\n  ")
      err(s"Failed to parse $path:\n  $errs")
    }
    Manifest(
      manifestPath = path,
      project = parseProject(toml),
      hardware = parseHardware(toml, path),
      software = parseSoftware(toml),
      platform = parsePlatform(toml),
      build = parseBuild(toml)
    )
  }

  // -- private helpers -------------------------------------------------------

  private def parseProject(t: TomlParseResult): ProjectSection = {
    val name = requiredString(t, "project.name")
    ProjectSection(name)
  }

  private def parseHardware(
      t: TomlParseResult,
      manifestPath: Path
  ): HardwareSection = {
    val srcDir = Option(t.getString("hardware.src-dir")).getOrElse("hw")
    val pathStr = Option(t.getString("hardware.beethoven-hardware.path"))
    val version = Option(t.getString("hardware.beethoven-hardware.version"))
    if (pathStr.isEmpty && version.isEmpty) {
      err(
        "[hardware.beethoven-hardware] requires either `path` (local checkout) or `version` (maven)."
      )
    }
    val resolvedPath = pathStr.map(s => os.Path(s, manifestPath / os.up))
    HardwareSection(srcDir, resolvedPath, version)
  }

  private def parseSoftware(t: TomlParseResult): SoftwareSection = {
    val srcDir = Option(t.getString("software.src-dir")).getOrElse("sw")
    SoftwareSection(srcDir)
  }

  private def parsePlatform(t: TomlParseResult): PlatformSection = {
    val target = requiredString(t, "platform.target")
    // build-mode is no longer in the manifest — it's a per-invocation
    // arg to cli/Run.scala. Any `[platform].build-mode` left over from
    // older manifests is silently ignored by tomlj's getTable below
    // (we only look at known keys + the per-target sub-table).
    val tbl = Option(t.getTable(s"platform.$target"))
    val params: Map[String, Any] = tbl match {
      case None => Map.empty
      case Some(table) =>
        table.keySet.asScala.iterator.map(k => k -> table.get(k)).toMap
    }
    PlatformSection(target, params)
  }

  private def parseBuild(t: TomlParseResult): BuildSection = {
    val outputDir = Option(t.getString("build.output-dir")).getOrElse("target")
    BuildSection(outputDir)
  }

  private def requiredString(t: TomlParseResult, dottedKey: String): String = {
    val v = t.getString(dottedKey)
    if (v == null) err(s"Missing required toml key: $dottedKey")
    v
  }

  private[beethoven] def err(msg: String): Nothing = {
    System.err.println("Beethoven: " + msg)
    sys.exit(1)
  }
}

/** Resolved on-disk locations derived deterministically from a [[Manifest]].
  *
  * Path conventions (assuming default `[build] output-dir = "target"`):
  *
  *   - `bindingRoot` → `<projectDir>/target/binding/`
  *     Mode-AGNOSTIC C bindings (`beethoven_hardware.{h,cc}`). Byte-identical
  *     between sim and synth on a given platform — this is a hard invariant.
  *
  *   - `rtlRoot` → `<projectDir>/target/<simulation|synthesis>/`
  *     Mode-specific output. Subdirectories:
  *       - `hw/` — chisel-generated `.sv`/`.v` (top, modules, harness for sim,
  *         AWS shell wrap for synth)
  *       - `logs/` — CLogger output
  *       - `aws/`, `implementation/`, `*.tcl`, `*.xdc` — synth-only,
  *         platform-specific
  *
  *   - `cacheRoot` → `<projectDir>/target/.cache/`
  *     Cross-mode shared memoization. Subdirectories:
  *       - `ips/` — Xilinx vendor IP `.xci` files (e.g. clock-FIFO)
  *       - `memories/<platformType>/<mode>/` — BRAM blackbox cache, keyed by
  *         `(platformType, mode)` because BRAM generation differs per pair
  *       - `SRs/` — shift-register Verilog blackboxes (shape-keyed, reusable)
  *       - `.test_synth/` — synth-side resource estimation scratch
  *       - `.tmp/` — per-run temp files (sed scripts, annotator scratch)
  *
  *   - `beethovenHardwareRoot` → root of the Beethoven-Hardware checkout
  *     itself (e.g. `<root>/bin/aws/scripts/initial_setup.sh` lives there).
  *     `Some(path)` when the manifest sets `[hardware.beethoven-hardware]
  *     path`, or in the legacy flow when `BEETHOVEN_PATH` env var is set.
  *     `None` when the project pulls Beethoven-Hardware from maven by
  *     version (no source tree to point at). Only AWS deploy helpers
  *     consume it; everything else ignores it.
  */
final case class BeethovenPaths(
    bindingRoot: Path,
    rtlRoot: Path,
    cacheRoot: Path,
    beethovenHardwareRoot: Option[Path]
)

object BeethovenPaths {
  def from(m: Manifest, buildMode: BuildMode): BeethovenPaths = {
    val outputRoot = m.manifestDir / m.build.outputDir
    val modeDir = buildMode match {
      case BuildMode.Synthesis  => "synthesis"
      case BuildMode.Simulation => "simulation"
    }
    BeethovenPaths(
      bindingRoot = outputRoot / "binding",
      rtlRoot = outputRoot / modeDir,
      cacheRoot = outputRoot / ".cache",
      beethovenHardwareRoot = m.hardware.beethovenHardwarePath
    )
  }
}
