package beethoven.Platforms.FPGA.Xilinx

import beethoven.{BeethovenBuild, BuildMode}
import beethoven.Platforms.{BuildModeKey, PlatformKey}
import org.chipsalliance.cde.config.Parameters

package object Templates {
  /** Root of the BRAM blackbox cache:
    * `target/.cache/memories/<platformType>/<mode>/`.
    *
    * Keyed on `(platformType, buildMode)` because
    * `MemoryStreams.Memory.scala:77` picks different generation paths per
    * pair, so naive shape-only sharing risks subtle miscompiles when users
    * switch modes. The directory is created on demand. Files placed here
    * are then symlinked into `paths.rtlRoot / "hw"` by `UniqueMv`, so
    * Vivado's TCL `add_files ../hw/` reads them transparently regardless
    * of where the cache lives.
    */
  private[Templates] def memoryRoot(implicit p: Parameters): os.Path = {
    val ptype = p(PlatformKey).platformType.toString
    val mode = p(BuildModeKey) match {
      case BuildMode.Synthesis  => "synthesis"
      case BuildMode.Simulation => "simulation"
    }
    val dir = BeethovenBuild.paths.cacheRoot / "memories" / ptype / mode
    if (!os.exists(dir)) os.makeDir.all(dir)
    dir
  }

  private[Templates] def writeF(
      port: String,
      withWriteEnable: Boolean,
      weWidth: Int,
      dataWidth: Int
  ): String = {
    if (withWriteEnable)
      f"""  if(CSB$port) begin
         |    for(gi=0;gi<$weWidth;gi=gi+1) begin
         |      if (WEB$port[gi]) begin
         |        mem[gi][A$port] <= I$port[(gi+1)*8-1-:8];
         |      end else begin
         |        memreg$port[(gi+1)*8-1-:8] <= mem[gi][A$port];
         |      end
         |    end
         |  end
         |""".stripMargin
    else
      f"""  if(CSB$port) begin
         |    if (WEB$port) begin
         |      mem[0][A$port] <= I$port[${dataWidth - 1}:0];
         |    end else begin
         |      memreg$port <= mem[0][A$port];
         |    end
         |  end
         |
         |""".stripMargin
  }
}
