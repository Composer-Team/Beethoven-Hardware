package basic

import beethoven.{AcceleratorConfig, AcceleratorSystemConfig, ReadChannelConfig, WriteChannelConfig}
import beethoven.Platforms.FPGA.Xilinx.AWS.{AWSF2Platform, DMAHelperConfig, MemsetHelper, MemsetHelperConfig}
import beethoven._
import chisel3._
import beethoven.Platforms.FPGA.Xilinx.Zynq.Kria2Platform

class MyAcceleratorConfig extends AcceleratorConfig(
  List.tabulate(4){ k =>
  AcceleratorSystemConfig(
    nCores = 1,
    name = "MyAcceleratorSystem",
    moduleConstructor = ModuleBuilder(p => new MyAccelerator(1 << k)(p)),
    memoryChannelConfig = List(
      ReadChannelConfig("vec_in", dataBytes = 1<<k),
      WriteChannelConfig("vec_out", dataBytes = 1<<k))
  )}
  )

object MyAcceleratorKria extends BeethovenBuild(new MyAcceleratorConfig,
  buildMode = BuildMode.Simulation,
  platform = new AWSF2Platform())

object MyAcceleratorKriaClocks extends BeethovenBuild(new MyAcceleratorConfig,
  buildMode = BuildMode.Synthesis,
  platform = new Kria2Platform())

object MyAcceleratorF2 extends BeethovenBuild(new MyAcceleratorConfig,
  buildMode = BuildMode.Synthesis,
  platform = new AWSF2Platform())