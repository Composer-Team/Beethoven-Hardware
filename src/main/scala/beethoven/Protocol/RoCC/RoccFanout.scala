package beethoven.Protocol.RoCC

import beethoven.Floorplanning.LazyModuleWithSLRs.LazyModuleWithFloorplan
import beethoven._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy._
import chisel3._
import chisel3.util.Arbiter

// Fan-out a single source to multiple sinks
class RoccFanout(implicit p: Parameters) extends LazyModule {
  val node = RoccNexusNode(
    dFn = { mp =>
      assert(mp.length == 1, "Cannot fan-in_cmd from multiple masters.")
      mp(0)
    },
    uFn = { sp =>
      val sids =
        sp.map(_.system_core_ids.map(_._1)).reduce(_ ++ _).toList.distinct
      val joined_core_maps = sids.map { sid =>
        val all_cores =
          sp.map(_.system_core_ids).toList.flatten.filter(_._1 == sid).map(_._2)
        val start = all_cores.map(_._1).min
        val max = all_cores.map(_._2).max
        all_cores.foreach { case (low, high) =>
          assert(
            low == start || all_cores.exists(_._2 + 1 == low),
            all_cores.toString()
          )
          assert(
            high == max || all_cores.exists(_._1 - 1 == high),
            all_cores.toString()
          )
        }
        (sid, (start, max))
      }
      RoCCSlaveParams(joined_core_maps)
    }
  )
  lazy val module = new LazyModuleImp(this) {
    val in_cmd = node.in(0)._1.req
    in_cmd.ready := false.B
    val outs = node.out

    outs.foreach { case (outB, outE) =>
      val cmd = outB.req
      val can_service_seq = outE.up.system_core_ids.map { case (sid, cores) =>
        val sys_match = in_cmd.bits.inst.system_id === sid.U
        val core_match =
          in_cmd.bits.inst.core_id >= cores._1.U && in_cmd.bits.inst.core_id <= cores._2.U
        sys_match && core_match
      }
      val can_service = VecInit(can_service_seq.toSeq).reduceTree(_ || _)
      cmd.valid := can_service && in_cmd.valid
      cmd.bits := in_cmd.bits
      when(can_service) {
        in_cmd.ready := cmd.ready
      }

      val dest_set = outE.up.system_core_ids.toSeq
      val is_lonesome =
        dest_set.length == 1 && dest_set(0)._2._1 == dest_set(0)._2._2
      if (is_lonesome) {
        outB.resp.bits.core_id := dest_set(0)._2._1.U
        outB.resp.bits.system_id := dest_set(0)._1.U
      }
    }

    // need to arbitrate for control of response
    val q = Module(new Arbiter(new AccelRoccResponse, outs.length))
    q.io.in.zip(outs.map(_._1.resp)).foreach { case (a, b) => a <> b }
    node.in(0)._1.resp <> q.io.out
  }
}

object RoccFanout {
  private var rocc_fanout_idx = 0
  def apply()(implicit p: Parameters): RoccNexusNode = LazyModuleWithFloorplan(
    new RoccFanout(), {
      val id = rocc_fanout_idx
      rocc_fanout_idx += 1
      s"zzrocc_fanout_$id"
    }
  ).node
  def apply(name: String)(implicit p: Parameters): RoccNexusNode =
    LazyModuleWithFloorplan(new RoccFanout(), name).node
}
