package systolic
import beethoven._
import chisel3._
import beethoven.Platforms.FPGA.Xilinx.AWS.AWSF2Platform
import dataclass.data

class SystolicArrayCmd extends AccelCommand("matmul") {
  val wgt_addr = UInt(64.W)
  val act_addr = UInt(64.W)
  val out_addr = UInt(64.W)
  val inner_dimension = UInt(20.W)
}
class SystolicArrayConfig(systolicArrayDim: Int, dataWidthBytes: Int)
    extends AcceleratorConfig(
      AcceleratorSystemConfig(
        nCores = 1,
        name = "SystolicArrayCore",
        moduleConstructor = new BlackboxBuilderCustom(
          Seq(
            BeethovenIOInterface(
              new SystolicArrayCmd,
              EmptyAccelResponse()
            )
          ),
          os.pwd / "src" / "test" / "verilog" / "systolic",
          externalDependencies = {
            val src_dir = os.pwd / "src" / "test" / "verilog" / "systolic"
            Some(
              Seq(
                src_dir / "ProcessingElement.v",
                src_dir / "ShiftReg.v",
                src_dir / "SystolicArray.v"
              )
            )
          }
        ),
        memoryChannelConfig = List(
          ReadChannelConfig(
            "weights",
            dataBytes = dataWidthBytes * systolicArrayDim
          ),
          ReadChannelConfig(
            "activations",
            dataBytes = dataWidthBytes * systolicArrayDim
          ),
          WriteChannelConfig(
            "vec_out",
            dataBytes = dataWidthBytes * systolicArrayDim
          )
        )
      )
    )

object SystolicArrayConfig
    extends BeethovenBuild(
      new SystolicArrayConfig(systolicArrayDim = 8, dataWidthBytes = 2),
      platform = new AWSF2Platform,
      buildMode = BuildMode.Simulation
    )
