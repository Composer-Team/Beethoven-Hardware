package basic

import beethoven.{AcceleratorConfig, AcceleratorSystemConfig, ReadChannelConfig, WriteChannelConfig}
import beethoven.Platforms.FPGA.Xilinx.AWS.{AWSF2Platform, DMAHelperConfig, MemsetHelper, MemsetHelperConfig}
import beethoven._
import chisel3._
import beethoven.Platforms.PlatformHasDMA
import beethovenTest.TestPaths

object KriaAXILDMATester {
  def main(args: Array[String]): Unit = {
    val buildMode = BuildMode.Simulation
    BeethovenBuild.run(
      config = new MyAcceleratorConfig(1),
      platform = new KriaPlatform() with PlatformHasDMA {
        override val DMABusWidthBytes: Int = 4
        override val DMAIDBits: Int = 0
        override val DMAisLite: Boolean = true
      },
      buildMode = buildMode,
      paths = TestPaths.local("KriaAXILDMATester", buildMode)
    )
  }
}