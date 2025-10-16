package beethoven.Protocol.AXI.AXILDMAEngine

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.tilelink._
import beethoven.common.CLog2Up

class AXIL_R_TO_TL(wAXI: Int, tbp: TLBundleParameters, tle: TLEdgeOut, credit_width: Int)
    extends Module {
  val tl = IO(TLBundle(tbp))

  val wTL = tl.d.bits.data.getWidth / 8
  val logWTL = CLog2Up(wTL)
  val logWAXI = CLog2Up(wAXI)
  val addrW = tl.a.bits.address.getWidth

  val axi = IO(new Bundle {
    val r = Decoupled(new Bundle {
      val data = UInt((wAXI * 8).W)
      val resp = UInt(2.W)
    })
    val ar = Flipped(Decoupled(new Bundle {
      val addr = UInt(addrW.W)
    }))
  })

  val available_credits = IO(Input(UInt(credit_width.W)))

  val s_idle :: s_active :: s_credit :: Nil = Enum(3)
  val state = RegInit(s_idle)

  axi.ar.ready := tl.a.ready && state === s_idle

  val aligned_addr = Cat(axi.ar.bits.addr(addrW - 1, logWTL), 0.U(logWTL.W))
  val is_read_credit = aligned_addr === 0.U
  val sel_width = CLog2Up(wTL / wAXI)
  val mux_sel = Reg(UInt(sel_width.W))

  println(logWTL - 1, logWAXI)
  val shamt: UInt = axi.ar.bits.addr(logWTL - 1, logWAXI)

  val hold = Reg(UInt((wAXI * 8).W))
  val hold_valid = Reg(Bool())

  axi.r.bits.data := DontCare
  axi.r.valid := false.B
  axi.r.bits.resp := 0.U
  tl.a.valid := false.B
  when(state === s_idle) {
    axi.ar.ready := true.B
    when(axi.ar.valid && (tl.a.ready || is_read_credit)) {
      mux_sel := shamt
      when(is_read_credit) {
        state := s_credit
      }.otherwise {
        state := s_active
        hold_valid := false.B
        tl.a.valid := true.B
      }
    }
  }.elsewhen(state === s_active) {
    val dat = VecInit(Seq.tabulate(wTL / wAXI) { m =>
      tl.d.bits.data((m + 1) * 8 * wAXI - 1, m * 8 * wAXI)
    })(mux_sel)

    tl.d.ready := !hold_valid
    when(tl.d.valid) {
      hold := dat
      hold_valid := true.B
    }
    
    axi.r.valid := hold_valid || tl.d.valid
    axi.r.bits.data := Mux(hold_valid, hold, dat)
    when(axi.r.fire) {
      state := s_idle
    }
  }.elsewhen(state === s_credit) {
    axi.r.valid := true.B
    axi.r.bits.data := available_credits
    when(axi.r.fire) {
      state := s_idle
    }
  }

  tl.a.bits := tle.Get(fromSource = 0.U, toAddress = aligned_addr, lgSize = logWTL.U)._2
  tl.d.ready := state === s_active
  axi.ar.valid && !is_read_credit
}
