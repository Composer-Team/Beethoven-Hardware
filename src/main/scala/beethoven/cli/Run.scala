package beethoven.cli

import beethoven.{BeethovenBuild, BeethovenPaths, Manifest}
import beethoven.Platforms.PlatformRegistry
import os.Path

/** Framework-side `sbt run` entry point.
  *
  * The user's project doesn't write a `BeethovenBuild`-extending object; the
  * project's generated build.sbt sets `Compile / mainClass := Some("beethoven.cli.Run")`
  * so this is what `sbt run` invokes.
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
    val manifestPath = parsed.manifestPath.getOrElse(os.pwd / "Beethoven.toml")
    val manifest = Manifest.parse(manifestPath)
    val paths = BeethovenPaths.from(manifest)
    val config =
      AcceleratorDiscovery.findUnique(manifest.hardware.srcDir)
    val platform = PlatformRegistry.fromConfig(
      manifest.platform.target,
      manifest.platform.params
    )
    BeethovenBuild.run(
      config = config,
      platform = platform,
      buildMode = manifest.platform.buildMode,
      paths = paths,
      userArgs = parsed.userArgs
    )
  }

  // -- arg parsing ----------------------------------------------------------

  private final case class ParsedArgs(
      manifestPath: Option[Path],
      userArgs: Map[String, Int]
  )

  private def parseArgs(args: Array[String]): ParsedArgs = {
    var manifestPath: Option[Path] = None
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
            "  --manifest <path>   explicit Beethoven.toml location\n" +
            "  -DKEY=INT           user-design integer arg (BuildArgs)"
        )
      }
    }
    ParsedArgs(manifestPath, userArgs.toMap)
  }
}
