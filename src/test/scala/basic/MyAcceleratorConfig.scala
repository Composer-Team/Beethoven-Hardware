package basic

import beethoven._
import beethoven.Platforms.FPGA.Xilinx.AWS._
import beethoven.common._
import beethovenTest.TestPaths
import chisel3._

class MyAcceleratorConfig(nCores: Int = 1)
    extends AcceleratorConfig(
      List.tabulate(4) { k =>
        AcceleratorSystemConfig(
          nCores = nCores,
          name = f"MyAcceleratorSystem${k}",
          moduleConstructor = ModuleBuilder(p => new MyAccelerator(1 << k)(p)),
          memoryChannelConfig = List(
            ReadChannelConfig("vec_in", dataBytes = 1 << k),
            WriteChannelConfig("vec_out", dataBytes = 1 << k)
          )
        )
      }
    )

object MyAcceleratorKria {
  def main(args: Array[String]): Unit = {
    val buildMode = BuildMode.Synthesis
    BeethovenBuild.run(
      config = new MyAcceleratorConfig,
      platform = new KriaPlatform(),
      buildMode = buildMode,
      paths = TestPaths.local("MyAcceleratorKria", buildMode)
    )
  }
}

object MyAcceleratorKriaClocks {
  def main(args: Array[String]): Unit = {
    val buildMode = BuildMode.Synthesis
    BeethovenBuild.run(
      config = new MyAcceleratorConfig,
      platform = new Kria2Platform(),
      buildMode = buildMode,
      paths = TestPaths.local("MyAcceleratorKriaClocks", buildMode)
    )
  }
}

object MyAcceleratorF2 {
  def main(args: Array[String]): Unit = {
    val buildMode = BuildMode.Simulation
    BeethovenBuild.run(
      config = new MyAcceleratorConfig(3),
      platform = new AWSF2Platform(),
      buildMode = buildMode,
      paths = TestPaths.local("MyAcceleratorF2", buildMode)
    )
  }
}

object VectorAddConfig {
  def main(args: Array[String]): Unit = {
    val buildMode = BuildMode.Synthesis
    BeethovenBuild.run(
      config = new VecAddConfig,
      platform = new AWSF2Platform(),
      buildMode = buildMode,
      paths = TestPaths.local("VectorAddConfig", buildMode)
    )
  }
}
