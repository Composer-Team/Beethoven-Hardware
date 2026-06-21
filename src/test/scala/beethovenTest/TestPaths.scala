package beethovenTest

import beethoven.{BeethovenPaths, BuildMode}

/** Ad-hoc [[BeethovenPaths]] for test/example build-entry objects that call
  * `BeethovenBuild.run` directly instead of going through the toml-driven
  * `beethoven.cli.Run` flow (which is the path real downstream projects use).
  */
object TestPaths {
  def local(name: String, mode: BuildMode): BeethovenPaths = {
    val modeDir = mode match {
      case BuildMode.Synthesis  => "synthesis"
      case BuildMode.Simulation => "simulation"
    }
    val root = os.pwd / "target" / "beethoven-test" / name
    BeethovenPaths(
      bindingRoot = root / "binding",
      rtlRoot = root / modeDir,
      cacheRoot = root / ".cache",
      beethovenHardwareRoot = None
    )
  }
}
