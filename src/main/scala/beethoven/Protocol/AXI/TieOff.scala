package beethoven.Protocol.AXI

import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import chisel3._
import beethoven._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.amba.axi4.{
  AXI4MasterNode,
  AXI4MasterParameters,
  AXI4MasterPortParameters
}

class TieOff()(implicit p: Parameters) extends LazyModule {
  val node = AXI4MasterNode(
    portParams = Seq(
      AXI4MasterPortParameters(
        masters = Seq(
          AXI4MasterParameters(
            name = "TieOff"
          )
        )
      )
    )
  )

  val module = new LazyModuleImp(this) {
    node.out(0)._1.ar.bits := DontCare
    node.out(0)._1.aw.bits := DontCare
    node.out(0)._1.w.bits := DontCare
    node.out(0)._1.ar.valid := false.B
    node.out(0)._1.aw.valid := false.B
    node.out(0)._1.w.valid := false.B
    node.out(0)._1.r.ready := false.B
    node.out(0)._1.b.ready := false.B
  }
}
