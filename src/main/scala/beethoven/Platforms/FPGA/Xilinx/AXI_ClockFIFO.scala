package beethoven.Platforms.FPGA.Xilinx

import beethoven.Platforms.FPGA.Xilinx.AXI_ClockFIFO.existing
import beethoven.BeethovenBuild
import beethoven.Protocol.AXI.AXI4Compat
import chisel3._
import os.Path

import scala.collection.mutable
import beethoven.common.tclMacro
import beethoven.Protocol.MasterPortParams

object AXI_ClockFIFO {
  private val existing = mutable.Set[(Int, Int, Int)]()
}

class AXI_ClockFIFO(
    addrWidth: Int,
    dataWidth: Int,
    idWidth: Int,
    synchronization_stages: Int = 3
) extends BlackBox {
  override val desiredName = f"AXI_FIFO_a${addrWidth}_d${dataWidth}_i${idWidth}"
  val dir = os.pwd / "ips"
  if (!os.exists(dir)) os.makeDir(dir)
  val io = IO(new Bundle {
    val m_axi_aclk = Input(Bool())
    val m_axi_aresetn = Input(Bool())
    val m_axi = new AXI4Compat(
      MasterPortParams(
        0,
        1L << addrWidth,
        dataWidth / 8,
        idWidth,
        dataWidth / 8 * 64
      )
    )

    val s_axi_aclk = Input(Bool())
    val s_axi_aresetn = Input(Bool())
    val s_axi = Flipped(
      new AXI4Compat(
        MasterPortParams(
          0,
          1L << addrWidth,
          dataWidth / 8,
          idWidth,
          dataWidth / 8 * 64
        )
      )
    )
  })
  if (existing.add((addrWidth, dataWidth, idWidth))) {
    val cmd =
      f"""create_ip -name axi_clock_converter -vendor xilinx.com -library ip -version 2.1 -module_name $desiredName
         |set_property -dict [list \\
         |  CONFIG.ADDR_WIDTH {$addrWidth} \\
         |  CONFIG.DATA_WIDTH {$dataWidth} \\
         |  CONFIG.ID_WIDTH {$idWidth} \\
         |  CONFIG.SYNCHRONIZATION_STAGES {$synchronization_stages} \\
         |] [get_ips $desiredName]
         |
         |generate_target all [get_files ips/$desiredName/$desiredName.xci]
         |synth_ip [get_files ips/$desiredName/$desiredName.xci]
         |""".stripMargin
    val pth = BeethovenBuild.IP_DIR / desiredName / f"$desiredName.xci"
    BeethovenBuild.addPostProcessorBundle(tclMacro(cmd, pth))
  }
}
