package beethoven.Protocol.AXI

import beethoven.Platforms.FPGA.Xilinx.AXI_ClockFIFO
import beethoven.Platforms.PlatformType
import beethoven.platform
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.amba.axi4.AXI4AdapterNode
import freechips.rocketchip.diplomacy._
import chisel3._

class ClockCrossing()(implicit p: Parameters) extends LazyModule {
  val node = AXI4AdapterNode()
  lazy val module = new ClockCrossingImp(this)
}

class ClockCrossingImp(outer: ClockCrossing)(implicit p: Parameters) extends LazyRawModuleImp(outer) {
  val io = IO(new Bundle {
    val clock_src = Input(Clock())
    val reset_src = Input(Bool())

    val clock_dst = Input(Clock())
    val reset_dst = Input(Bool())
  })

  if (platform.platformType != PlatformType.FPGA) {
    System.err.println("Clock crossing implementation currently relies on dual-clocked BRAM (FPGA-only)," +
      " so other platforms are not yet supported.")
    System.exit(0)
  }

  outer.node.out.zip(outer.node.in).foreach { case ((bundle_o, param_o), (bundle_i, param_i)) =>
    // maintain a separate FIFO for each channel because they are completely independent by specification
    val cross = Module(new AXI_ClockFIFO(param_i.bundle.addrBits, param_i.bundle.dataBits, param_i.bundle.idBits))
    AXI4Compat.connectCompatMaster(cross.io.s_axi, bundle_i)
    AXI4Compat.connectCompatSlave(cross.io.m_axi, bundle_o)
    cross.io.s_axi_aclk := io.clock_src.asBool
    cross.io.s_axi_aresetn := io.reset_src
    cross.io.m_axi_aclk := io.clock_dst.asBool
    cross.io.m_axi_aresetn := io.reset_dst
  }
}
