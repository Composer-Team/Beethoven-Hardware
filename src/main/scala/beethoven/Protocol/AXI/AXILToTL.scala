package beethoven.Protocol.AXI

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.amba.axi4.AXI4ToTLNode
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.amba.axi4._
import org.chipsalliance.diplomacy.tilelink._
import org.chipsalliance.diplomacy.nodes._
import beethoven.common.CLog2Up
case class AXILToDifferentWidthTLNode(val axiWidthBytes: Int)(implicit valName: ValName)
    extends MixedAdapterNode(AXI4Imp, TLImp)(
      dFn = { case mp =>
        TLMasterPortParameters.v1(clients = Seq(TLMasterParameters.v1(name = "AXILToDWTL")))
      },
      uFn = { mp =>
        AXI4SlavePortParameters(
          slaves = mp.managers.map { m =>
            AXI4SlaveParameters(
              address = m.address,
              supportsWrite = TransferSizes(axiWidthBytes),
              supportsRead = TransferSizes(axiWidthBytes),
              interleavedId = Some(0)
            )
          }, // TL2 never interleaves D beats
          beatBytes = axiWidthBytes
        )
      }
    )

class AXILToTL(wAXI: Int)(implicit
    p: Parameters
) extends LazyModule {
  val node = AXILToDifferentWidthTLNode(wAXI)
  lazy val module = new LazyModuleImp(this) {
    val axi = node.in(0)._1
    val tl = node.out(0)._1
    val wTL = tl.d.bits.data.getWidth / 8
    val logWTL = CLog2Up(wTL)
    val logWAXI = CLog2Up(wAXI)
    val addrW = tl.a.bits.address.getWidth
    val idle :: active_read :: active_write :: Nil = Enum(3)
    val state = RegInit(idle)
    val in_flight = state =/= idle
    // prefer read over write because we need to buffer writes anyway
    val interrupt_write = axi.ar.valid
    // WRITE MACHINE
    val write_queues_valid = locally {
      val wQ = Queue(axi.w)
      val awQ = Queue(axi.aw)
      awQ.ready := awQ.valid && wQ.valid && !in_flight && !interrupt_write
      wQ.ready := awQ.valid && wQ.valid && !in_flight && !interrupt_write
      when(!interrupt_write) {
        state := active_write
        tl.a.bits.address := Cat(awQ.bits.addr(addrW - 1, logWTL), 0.U(logWTL.W))
        tl.a.bits.opcode := TLMessages.PutFullData
        val shamt: UInt = axi.aw.bits.addr(logWTL - 1, logWAXI)
        tl.a.bits.mask := (if (wAXI == wTL) {
                             BigInt("1" * wAXI, 2).U
                           } else if (wAXI < wTL) {
                             BigInt("1" * wAXI, 2).U << (CLog2Up(logWAXI).U * shamt)
                           } else {
                             throw new Exception("Cannot have an AXIL width greater than TL width")
                           })
        tl.a.bits.size := logWTL.U
        tl.a.bits.data := wQ.bits.data << (8.U * shamt)
      }
      when(state === active_write) {
        when(tl.d.fire) {
          state := idle
        }
      }
      awQ.valid && wQ.valid
    }

    // READ MACHINE
    val read_machine_valid = locally {
      axi.ar.ready := tl.a.ready && !in_flight
      when(axi.ar.valid) {
        tl.a.bits.opcode := TLMessages.Get
        tl.a.bits.corrupt := false.B
        tl.a.bits.size := logWTL.U
        tl.a.bits.source := 0.U
        tl.a.bits.address := Cat(axi.ar.bits.addr(addrW - 1, logWTL), 0.U(logWTL.W))
        when(axi.ar.fire) {
          state := active_read
        }
        tl.a.bits.mask := BigInt("1" * wTL, 2).U
        val sel_width = CLog2Up(wTL / wAXI)
        val mux_sel =
          if (wAXI == wTL) 0.U(sel_width.W)
          else Reg(UInt(sel_width.W))
        val shamt: UInt = axi.ar.bits.addr(logWTL - 1, logWAXI)
        when(tl.a.fire) {
          mux_sel := shamt
        }
        axi.r.valid := tl.d.valid
        axi.r.bits.data := VecInit(Seq.tabulate(wTL / wAXI) { m =>
          tl.d.bits.data((m + 1) * 8 * wAXI - 1, m * 8 * wAXI)
        })(mux_sel)
        axi.r.bits.id := 0.U
        axi.r.bits.last := true.B
        tl.d.ready := axi.r.ready
        when(axi.r.fire) {
          state := idle
        }
      }
      axi.ar.valid
    }

    tl.a.valid := (read_machine_valid || write_queues_valid) && !in_flight
  }
}
