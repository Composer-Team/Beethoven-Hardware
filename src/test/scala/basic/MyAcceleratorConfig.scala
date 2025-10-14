package basic

import beethoven.{AcceleratorConfig, AcceleratorSystemConfig, ReadChannelConfig, WriteChannelConfig}
import beethoven.Platforms.FPGA.Xilinx.AWS.{AWSF2Platform, DMAHelperConfig, MemsetHelper, MemsetHelperConfig}
import beethoven._
import chisel3._

class MyAcceleratorConfig(nCores: Int = 1) extends AcceleratorConfig(
  List.tabulate(4){ k =>
  AcceleratorSystemConfig(
    nCores = nCores,
    name = f"MyAcceleratorSystem${k}",
    moduleConstructor = ModuleBuilder(p => new MyAccelerator(1 << k)(p)),
    memoryChannelConfig = List(
      ReadChannelConfig("vec_in", dataBytes = 1<<k),
      WriteChannelConfig("vec_out", dataBytes = 1<<k))
  )}
  )

object MyAcceleratorKria extends BeethovenBuild(new MyAcceleratorConfig,
  buildMode = BuildMode.Synthesis, 
  platform = new KriaPlatform())

object MyAcceleratorKriaClocks extends BeethovenBuild(new MyAcceleratorConfig,
  buildMode = BuildMode.Synthesis,
  platform = new Kria2Platform())

object MyAcceleratorF2 extends BeethovenBuild(new MyAcceleratorConfig(5),
  buildMode = BuildMode.Synthesis,
  platform = new AWSF2Platform())