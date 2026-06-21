package systolic.chisel
import beethoven._
import chisel3._
import beethoven.Platforms.FPGA.Xilinx.AWS.AWSF2Platform
import systolic.Constants._
import beethoven.Generation.CppGeneration
import beethovenTest.TestPaths

class SystolicArrayCmd extends AccelCommand("matmul") {
  val wgt_addr = UInt(64.W)
  val act_addr = UInt(64.W)
  val out_addr = UInt(64.W)
  val inner_dimension = UInt(20.W)
}

class SystolicArrayChiselConfig(nCores: Int)
    extends AcceleratorConfig(
      AcceleratorSystemConfig(
        nCores = nCores,
        name = "SystolicArrayCore",
        moduleConstructor = new ModuleBuilder(p => new SystolicArrayCore(systolic_array_dim)(p)),
        memoryChannelConfig = List(
          ReadChannelConfig(
            "weights",
            dataBytes = data_width_bytes * systolic_array_dim
          ),
          ReadChannelConfig(
            "activations",
            dataBytes = data_width_bytes * systolic_array_dim
          ),
          WriteChannelConfig(
            "vec_out",
            dataBytes = data_width_bytes * systolic_array_dim
          )
        )
      )
    )

object SystolicArrayConfig {
  def main(args: Array[String]): Unit = {
    val buildMode = BuildMode.Synthesis
    val config = {
      CppGeneration.addPreprocessorDefinition(
        Seq(
          ("DATA_WIDTH_BYTES", data_width_bytes),
          ("FRAC_BITS", frac_bits),
          ("INT_BITS", int_bits),
          ("SYSTOLIC_ARRAY_DIM", systolic_array_dim)
        )
      )
      new SystolicArrayChiselConfig(1)
    }
    BeethovenBuild.run(
      config = config,
      platform = new KriaPlatform,
      buildMode = buildMode,
      paths = TestPaths.local("SystolicArrayConfig-chisel", buildMode)
    )
  }
}
