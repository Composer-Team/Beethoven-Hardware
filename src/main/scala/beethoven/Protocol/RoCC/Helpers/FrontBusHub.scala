package beethoven.Protocol.RoCC.Helpers

import beethoven.Protocol.RoCC.{RoccClientNode, RoccMasterParams}
import beethoven.platform
import chisel3._
import org.chipsalliance.cde.config._
import org.chipsalliance.diplomacy.amba.axi4.AXI4IdentityNode
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.tilelink.TLIdentityNode

class FrontBusHub(implicit p: Parameters) extends LazyModule {
  val widget = LazyModule(new FrontBusWidget())
  val axi_in = AXI4IdentityNode()
  widget.node := axi_in

  val rocc_out = RoccClientNode(RoccMasterParams())

  lazy val module = new AXILHubModule(this)(p)
}

class AXILHubModule(outer: FrontBusHub)(implicit p: Parameters) extends LazyModuleImp(outer) {
  val axil_widget = outer.widget.module

  val axil_to_rocc = Module(new AXILToRocc)
  val rocc_to_axil = Module(new RoccToAXIL)

  val io = IO(new Bundle {
    val cache_prot =
      if (platform.hasDebugAXICACHEPROT) Some(Output(UInt(7.W))) else None
  })
  if (io.cache_prot.isDefined)
    io.cache_prot.get := axil_widget.io.cache_prot.get

  axil_widget.io.resp <> rocc_to_axil.io.out
  rocc_to_axil.io.rocc <> outer.rocc_out.out(0)._1.resp
  axil_to_rocc.io.in <> axil_widget.io.cmds

  val rocc_out = outer.rocc_out.out(0)._1.req
  val rocc_cmd = axil_to_rocc.io.rocc
  rocc_out.valid := rocc_cmd.valid
  rocc_cmd.ready := rocc_out.ready
  rocc_out.bits := rocc_cmd.bits
}
