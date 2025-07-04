package beethoven

import chipsalliance.rocketchip.config.{Config, Parameters}
import beethoven.Generation._
import beethoven.Platforms.FPGA.Xilinx.AWS.tclMacro
import beethoven.Platforms.FPGA.Xilinx.Templates.SynthScript
import beethoven.Platforms.FPGA.Xilinx.getTclMacros
import beethoven.Platforms.PlatformType.PlatformType
import beethoven.Platforms._
import beethoven.Protocol.FrontBus.{AXIFrontBusProtocol, FrontBusProtocol}
import os.Path


case class KriaPlatform(memoryNChannels: Int = 1,
                        override val clockRateMHz: Int = 100,
                        overrideMemoryBusWidthBytes: Option[Int] = None,
                        override val hasDebugAXICACHEPROT: Boolean = false)
  extends Platform with HasPostProccessorScript with HasXilinxMem {

  override val platformType: PlatformType = PlatformType.FPGA
  override val hasDiscreteMemory: Boolean = false
  override val isActiveHighReset: Boolean = false

  override val frontBusBaseAddress: Long = 0x2000000000L
  override val frontBusAddressNBits: Int = 40
  override val frontBusAddressMask: Long = 0xffffL
  override val frontBusBeatBytes: Int = 4
  override val frontBusProtocol: FrontBusProtocol = new AXIFrontBusProtocol(false)

  override val physicalMemoryBytes: Long = 4L << 30
  override val memorySpaceAddressBase: Long = 0x0
  override val memorySpaceSizeBytes: BigInt = 1L << 49
  override val memoryControllerIDBits: Int = 6
  override val memoryControllerBeatBytes: Int = overrideMemoryBusWidthBytes match {
    case None => 16
    case Some(x) =>
      if (!Seq(4, 8, 16).contains(x)) throw new Exception("Bus width must be 4B, 8B, or 16B")
      x
  }

  override def postProcessorMacro(p: Parameters, paths: Seq[Path]): Unit = {
    if (p(BuildModeKey) == BuildMode.Synthesis) {
      val s = SynthScript(
        "beethoven",
        "output",
        "xck26-sfvc784-2LV-c",
        "xilinx.com:kv260_som:part0:1.4",
        clockRateMHz.toString,
        precompile_dependencies = getTclMacros()
      )
      os.write.over(BeethovenBuild.top_build_dir / "synth.tcl",
        s.setup + "\n" + s.run)
      os.write.over(BeethovenBuild.top_build_dir / "ip.tcl",
        BeethovenBuild.postProcessorBundles.filter(_.isInstanceOf[tclMacro]).map(_.asInstanceOf[tclMacro].cmd).mkString("\n"))
    }
  }

  override val physicalDevices: List[DeviceConfig] = List(DeviceConfig(0, "SLR0"))
  override val physicalInterfaces: List[PhysicalInterface] = List(PhysicalHostInterface(0),
    PhysicalMemoryInterface(0, 0))
  override val physicalConnectivity: List[(Int, Int)] = List.empty
  override val nURAMs: Map[Int, Int] = Map { (0, 64) }
  override val nBRAMs: Map[Int, Int] = Map { (0, 144) }
}