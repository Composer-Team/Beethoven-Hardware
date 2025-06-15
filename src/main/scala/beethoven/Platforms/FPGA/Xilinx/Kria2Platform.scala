package beethoven

import beethoven.Platforms.BuildModeKey
import beethoven.Platforms.FPGA.Xilinx.AWS.tclMacro
import beethoven.Platforms.FPGA.Xilinx.Templates.SynthScript
import beethoven.Platforms.FPGA.Xilinx.getTclMacros
import beethoven.Protocol.FrontBus._
import chipsalliance.rocketchip.config.Parameters
import os.Path

/**
 * Modification to Kria to test clock crossing.
 */
class Kria2Platform(override val memoryNChannels: Int = 1,
                         override val clockRateMHz: Int = 100,
                         override val overrideMemoryBusWidthBytes: Option[Int] = None,
                         override val hasDebugAXICACHEPROT: Boolean = false)
  extends KriaPlatform(memoryNChannels, clockRateMHz, overrideMemoryBusWidthBytes, hasDebugAXICACHEPROT) {

  override val frontBusProtocol = new AXIFrontBusProtocolFastMem(false)

  override def postProcessorMacro(p: Parameters, paths: Seq[Path]): Unit = {
    if (p(BuildModeKey) == BuildMode.Synthesis) {
      val s = SynthScript(
        "beethoven",
        "output",
        "xck26-sfvc784-2LV-c",
        "xilinx.com:kv260_som:part0:1.4",
        clockRateMHz.toString,
        precompile_dependencies = getTclMacros(),
        setup_ssp = "KriaSetupDoubleClock.ssp",
        extra_arguments = Map(
          "clock_rate2" -> "200"
        )
      )
      os.write.over(BeethovenBuild.top_build_dir / "synth.tcl",
        s.setup + "\n" + s.run)
      os.write.over(BeethovenBuild.top_build_dir / "ip.tcl",
        BeethovenBuild.postProcessorBundles.filter(_.isInstanceOf[tclMacro]).map(_.asInstanceOf[tclMacro].cmd).mkString("\n"))
    }
  }

}

//