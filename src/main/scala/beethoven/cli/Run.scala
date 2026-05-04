package beethoven.cli

import beethoven.{BeethovenBuild, BeethovenPaths, BuildMode, Manifest}
import beethoven.Platforms.PlatformRegistry
import os.Path

/** Framework-side `sbt run` entry point.
  *
  * The user's project doesn't write a `BeethovenBuild`-extending object; the
  * project's generated build.sbt sets `Compile / mainClass := Some("beethoven.cli.Run")`
  * so this is what `sbt run` invokes.
  *
  * Required:
  *   - `--mode <simulation|synthesis>` (or `--mode=<value>`): build mode.
  *     Mode is per-invocation, *not* in the manifest — see Manifest.scala
  *     for the rationale. If omitted, prints a usage hint and exits 1.
  *
  * Accepts:
  *   - `--manifest <path>` (or `--manifest=<path>`): explicit Beethoven.toml
  *     location. Resolved relative to CWD if not absolute. If omitted, looks
  *     for `Beethoven.toml` directly in CWD (no walk-up). Walk-up auto-
  *     discovery is the responsibility of the external `beethoven` CLI; this
  *     entry point assumes sbt is invoked from the project root.
  *   - `-DKEY=INT`: user-design-arg channel (existing `BuildArgs` flow).
  *     Distinct from framework config.
  */
object Run {

  def main(args: Array[String]): Unit = {
    val parsed = parseArgs(args)
    val mode = parsed.mode.getOrElse {
      printUsage()
      sys.exit(1)
    }
    val manifestPath = parsed.manifestPath.getOrElse(os.pwd / "Beethoven.toml")
    val manifest = Manifest.parse(manifestPath)
    val paths = BeethovenPaths.from(manifest, mode)
    val config =
      AcceleratorDiscovery.findUnique(manifest.hardware.srcDir)
    val platform = PlatformRegistry.fromConfig(
      manifest.platform.target,
      manifest.platform.params
    )
    BeethovenBuild.run(
      config = config,
      platform = platform,
      buildMode = mode,
      paths = paths,
      userArgs = parsed.userArgs
    )
  }

  // -- arg parsing ----------------------------------------------------------

  private final case class ParsedArgs(
      manifestPath: Option[Path],
      mode: Option[BuildMode],
      userArgs: Map[String, Int]
  )

  private def parseArgs(args: Array[String]): ParsedArgs = {
    var manifestPath: Option[Path] = None
    var mode: Option[BuildMode] = None
    val userArgs = scala.collection.mutable.LinkedHashMap.empty[String, Int]
    var i = 0
    while (i < args.length) {
      val a = args(i)
      if (a == "--manifest") {
        if (i + 1 >= args.length) {
          Manifest.err("--manifest requires a path argument.")
        }
        manifestPath = Some(os.Path(args(i + 1), os.pwd))
        i += 2
      } else if (a.startsWith("--manifest=")) {
        manifestPath = Some(os.Path(a.stripPrefix("--manifest="), os.pwd))
        i += 1
      } else if (a == "--mode") {
        if (i + 1 >= args.length) {
          Manifest.err("--mode requires a value (simulation or synthesis).")
        }
        mode = Some(parseMode(args(i + 1)))
        i += 2
      } else if (a.startsWith("--mode=")) {
        mode = Some(parseMode(a.stripPrefix("--mode=")))
        i += 1
      } else if (a.startsWith("-D")) {
        val kv = a.substring(2).split("=", 2)
        if (kv.length != 2) {
          Manifest.err(
            s"Malformed user arg '$a'. Expected -DKEY=INT (e.g. -DCORES=4)."
          )
        }
        val v =
          try kv(1).toInt
          catch {
            case _: NumberFormatException =>
              Manifest.err(
                s"User arg '$a' value '${kv(1)}' is not an integer."
              )
          }
        userArgs(kv(0)) = v
        i += 1
      } else {
        Manifest.err(
          s"Unknown argument: '$a'. Valid:\n" +
            "  --mode <simulation|synthesis>   build mode (required)\n" +
            "  --manifest <path>               explicit Beethoven.toml location\n" +
            "  -DKEY=INT                       user-design integer arg (BuildArgs)"
        )
      }
    }
    ParsedArgs(manifestPath, mode, userArgs.toMap)
  }

  private def parseMode(s: String): BuildMode = s match {
    case "simulation" => BuildMode.Simulation
    case "synthesis"  => BuildMode.Synthesis
    case other =>
      Manifest.err(
        s"--mode='$other' is invalid. Use 'simulation' or 'synthesis'."
      )
  }

  /** Friendly usage when --mode is missing. Goes to stdout and exits 1 —
    * sbt run with no useful work done is a non-success.
    */
  private def printUsage(): Unit = {
    val msg =
      """beethoven.cli.Run requires --mode.
        |
        |Usage:
        |  sbt "run --mode <simulation|synthesis>"
        |
        |Examples:
        |  sbt "run --mode simulation"
        |  sbt "run --mode synthesis"
        |
        |Other args (optional):
        |  --manifest <path>   explicit Beethoven.toml location (default: ./Beethoven.toml)
        |  -DKEY=INT           pass an integer to BuildArgs (e.g. -DCORES=4)
        |
        |Or use the `beethoven` CLI which sets --mode automatically:
        |  beethoven sim                # --mode simulation
        |  beethoven run                # --mode synthesis
        |  beethoven build hw           # --mode simulation
        |  beethoven build hw --release # --mode synthesis
        |""".stripMargin
    println(msg)
  }
}
