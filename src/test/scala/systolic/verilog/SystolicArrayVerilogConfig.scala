package systolic.verilog
import beethoven._
import chisel3._
import beethoven.Platforms.FPGA.Xilinx.AWS.AWSF2Platform
import systolic._
import systolic.Constants.systolic_array_dim
import systolic.Constants.data_width_bits
import systolic.Constants.int_bits
import systolic.Constants.frac_bits
import beethoven.Generation.CppGeneration

class SystolicArrayCmd extends AccelCommand("matmul") {
  val wgt_addr = UInt(64.W)
  val act_addr = UInt(64.W)
  val out_addr = UInt(64.W)
  val inner_dimension = UInt(20.W)
}

class SystolicArrayConfig(nCores: Int)
    extends AcceleratorConfig(
      AcceleratorSystemConfig(
        nCores = nCores,
        name = "SystolicArrayCore",
        moduleConstructor = new BlackboxBuilderCustom(
          Seq(
            BeethovenIOInterface(
              new SystolicArrayCmd,
              EmptyAccelResponse()
            )
          ),
          sourcePath = os.pwd / "src" / "test" / "resources" / "systolic",
          externalDependencies = {
            val src_dir = os.pwd / "src" / "test" / "resources" / "systolic"
            Some(
              Seq(
                src_dir / "ProcessingElement.v",
                src_dir / "ShiftReg.v",
                src_dir / "SystolicArray.v"
              )
            )
          },
          verilogMacroParams = Map(
            "SYSTOLIC_ARRAY_DIM" -> systolic_array_dim,
            "DATA_WIDTH_BITS" -> data_width_bits,
            "INT_BITS" -> int_bits,
            "FRAC_BITS" -> frac_bits
          )
        ),
        memoryChannelConfig = List(
          ReadChannelConfig(
            "weights",
            dataBytes = Constants.data_width_bytes * Constants.systolic_array_dim
          ),
          ReadChannelConfig(
            "activations",
            dataBytes = Constants.data_width_bytes * Constants.systolic_array_dim
          ),
          WriteChannelConfig(
            "vec_out",
            dataBytes = Constants.data_width_bytes * Constants.systolic_array_dim
          )
        )
      )
    )

object SystolicArrayConfig
    extends BeethovenBuild(
      {
        CppGeneration.addPreprocessorDefinition(
          Seq(
            ("DATA_WIDTH_BYTES", Constants.data_width_bytes),
            ("FRAC_BITS", frac_bits),
            ("INT_BITS", int_bits),
            ("SYSTOLIC_ARRAY_DIM", systolic_array_dim)
          )
        )

        new SystolicArrayConfig(1)
      },
      platform = new AWSF2Platform,
      buildMode = BuildMode.Simulation
    )
