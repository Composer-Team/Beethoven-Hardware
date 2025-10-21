package systolic.chisel
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

class SystolicArrayChiselConfig(systolicArrayDim: Int, dataWidthBytes: Int)
    extends AcceleratorConfig(
      AcceleratorSystemConfig(
        nCores = 1,
        name = "SystolicArrayCore",
        moduleConstructor = new ModuleBuilder(p => new SystolicArrayCore()(p)),
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
      new SystolicArrayChiselConfig(systolicArrayDim = 8, dataWidthBytes = 2),
      platform = new AWSF2Platform,
      buildMode = BuildMode.Simulation
    )
