package beethoven.Platforms

import beethoven.Platforms.FPGA.Xilinx.AWS.{AWSF1Platform, AWSF2Platform}
import beethoven.{
  AUPZU3Platform,
  KriaPlatform,
  Kria2Platform,
  Manifest
}

/** Maps `[platform] target` strings from Beethoven.toml to concrete
  * [[Platform]] instances, populated from the matching `[platform.<target>]`
  * table.
  *
  * Per-platform required-key validation lives next to each match arm, so
  * adding a platform means editing one place.
  */
object PlatformRegistry {

  private val validTargets: Seq[String] = Seq(
    "aupzu3",
    "kria",
    "kria2",
    "aws-f1",
    "aws-f2",
    "simulation"
  )

  def fromConfig(target: String, params: Map[String, Any]): Platform = {
    val P = new Params(target, params)
    target match {
      case "aupzu3" =>
        AUPZU3Platform(
          DRAMSizeGB = P.int("dram-size-gb"),
          memoryNChannels = P.intOr("memory-channels", 1),
          clockRateMHz = P.intOr("clock-rate-mhz", 100)
        )

      case "kria" =>
        KriaPlatform(
          memoryNChannels = P.intOr("memory-channels", 1),
          clockRateMHz = P.intOr("clock-rate-mhz", 100)
        )

      case "kria2" =>
        new Kria2Platform(
          memoryNChannels = P.intOr("memory-channels", 1),
          clockRateMHz = P.intOr("clock-rate-mhz", 100)
        )

      case "aws-f1" =>
        new AWSF1Platform(
          memoryNChannels = P.int("memory-channels"),
          clock_recipe = P.strOr("clock-recipe", "A0")
        )

      case "aws-f2" =>
        new AWSF2Platform(
          remoteUsername = P.strOr("remote-username", "ubuntu")
        )

      case "simulation" =>
        new SimulationPlatform(P.intOr("clock-rate-mhz", 100))

      case other =>
        Manifest.err(
          s"Unknown [platform] target='$other'. Valid targets: " +
            validTargets.mkString(", ") + "."
        )
    }
  }

  /** Typed accessors over the raw `Map[String, Any]` from tomlj. tomlj boxes
    * ints as `java.lang.Long`, so we coerce on read. Wrong types become clear
    * error messages keyed on the toml location.
    */
  private final class Params(target: String, m: Map[String, Any]) {
    private def loc(key: String): String = s"[platform.$target] $key"

    def int(key: String): Int = m.get(key) match {
      case Some(v: java.lang.Long) => v.intValue
      case Some(v: Int)            => v
      case Some(other) =>
        Manifest.err(
          s"${loc(key)} must be an integer; got ${other.getClass.getSimpleName} ($other)."
        )
      case None => Manifest.err(s"Missing required key ${loc(key)}.")
    }

    def intOr(key: String, default: Int): Int =
      if (m.contains(key)) int(key) else default

    def str(key: String): String = m.get(key) match {
      case Some(v: String) => v
      case Some(other) =>
        Manifest.err(
          s"${loc(key)} must be a string; got ${other.getClass.getSimpleName} ($other)."
        )
      case None => Manifest.err(s"Missing required key ${loc(key)}.")
    }

    def strOr(key: String, default: String): String =
      if (m.contains(key)) str(key) else default
  }
}
