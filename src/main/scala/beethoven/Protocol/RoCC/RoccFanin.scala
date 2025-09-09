package beethoven.Protocol.RoCC

import beethoven.Floorplanning.LazyModuleWithSLRs.LazyModuleWithFloorplan
import beethoven._
import chipsalliance.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import chisel3._
import chisel3.util._

// Fan-in multiply command sources to a single sink
class RoccFanin(implicit p: Parameters) extends LazyModule {
  val node = RoccNexusNode(
    dFn = { sm => sm(0) },
    uFn = { ss =>
      assert(ss.length == 1, "Cannot fanin to more than one slave")
      ss(0)
    }
  )

  lazy val module = new LazyModuleImp(this) {
    // multiple command inputs
    val ins = node.in
    ins.zipWithIndex.foreach(a =>
      a._1._1.req.suggestName(f"input_lane_${a._2}")
    )
    // one command output
    val out = node.out(0)
    if (ins.length == 1) {
      val ir = ins(0)._1.req
      val or = out._1.req
      ir.ready := or.ready
      or.valid := ir.valid
      or.bits := ir.bits

      val ire = ins(0)._1.resp
      val ore = out._1.resp
      ire.valid := ore.valid
      ore.ready := ire.ready
      ire.bits := ore.bits
    } else {
      // IFF a command passes through with 'xd' high (expect response bit), then we need to remember the master it came
      // from. The payload does not include identifying master behavior and adding such a thing would take up space
      // in the payload. SO, we need to be able to map from sys+cmd id pair to master
      val cmd_arbiter = Module(new Arbiter(new AccelRoccCommand(), ins.length))
      ins.map(_._1.req).zip(cmd_arbiter.io.in).foreach { case (a, b) => a <> b }
      cmd_arbiter.io.out <> out._1.req

      val returnHit = Wire(UInt(log2Up(ins.length + 1).W))
      returnHit := 0.U

      val resp_core_id = out._1.resp.bits.core_id
      val resp_sys_id = out._1.resp.bits.system_id

      out._2.up.system_core_ids.foreach { case (sid, cores) =>
        val nCores = cores._2 - cores._1 + 1
        val master_source = Reg(Vec(nCores, UInt(log2Up(ins.length).W)))
        val lookup_valid: Vec[Bool] = Reg(Vec(nCores, Bool()))
        when(reset.asBool) {
          lookup_valid.foreach(_ := false.B)
        }
        val sys_match = out._1.req.bits.getSystemID === sid.U
        val core_adj = out._1.req.bits.getCoreID - cores._1.U

        when(out._1.req.fire && sys_match && out._1.req.bits.inst.xd) {
          master_source(core_adj) := cmd_arbiter.io.chosen
          lookup_valid(core_adj) := true.B
          assert(
            out._1.req.bits.getCoreID >= cores._1.U && out._1.req.bits.getCoreID <= cores._2.U,
            "Core requested is out of range of available cores... Expect (%d, lo: %d, hi: %d )",
            out._1.req.bits.getCoreID,
            cores._1.U,
            cores._2.U
          )
        }

        // do we hit for this system?
        val resp_core_adj = resp_core_id - cores._1.U
        val localHit = resp_sys_id === sid.U &&
          lookup_valid(resp_core_adj) &&
          resp_core_id >= cores._1.U &&
          resp_core_id <= cores._2.U
        val hit_lookup = master_source(resp_core_adj)
        when(localHit) {
          returnHit := hit_lookup
          when(out._1.resp.fire) {
            lookup_valid(resp_core_adj) := 0.U
          }
        }

        master_source
      }

      ins.zipWithIndex.foreach { case (a, idx) =>
        a._1.resp.valid := idx.U === returnHit && out._1.resp.valid
        a._1.resp.bits := out._1.resp.bits
        when(idx.U === returnHit) {
          out._1.resp.ready := a._1.resp.ready
        }
      }
    }
  }
}

object RoccFanin {
  private var rocc_fanin_idx = 0
  def apply()(implicit p: Parameters): RoccNexusNode = LazyModuleWithFloorplan(
    new RoccFanin(), {
      val id = rocc_fanin_idx
      rocc_fanin_idx += 1
      s"zzrocc_fanin_$id"
    }
  ).node
  def apply(name: String)(implicit p: Parameters): RoccNexusNode =
    LazyModuleWithFloorplan(new RoccFanin(), name).node
}
