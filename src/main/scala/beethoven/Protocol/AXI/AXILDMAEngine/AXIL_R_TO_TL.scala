package beethoven.Protocol.AXI.AXILDMAEngine

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.tilelink._
import beethoven.common.CLog2Up

class AXIL_R_TO_TL(wAXI: Int, tbp: TLBundleParameters, tle: TLEdgeOut, credit_width: Int) extends Module {
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

  val idle = RegInit(false.B)

  axi.ar.ready := tl.a.ready && idle
  when(!idle) {
    tl.d.ready := axi.r.ready
    when(tl.d.fire) {
      idle := true.B
    }
  }

  val aligned_addr = Cat(axi.ar.bits.addr(addrW - 1, logWTL), 0.U(logWTL.W))
  val is_read_credit = aligned_addr === 0.U
  val sel_width = CLog2Up(wTL / wAXI)

  val mux_sel =
    if (wAXI == wTL) 0.U(sel_width.W)
    else Reg(UInt(sel_width.W))
  val shamt: UInt = axi.ar.bits.addr(logWTL - 1, logWAXI)

  axi.r.bits.data := Mux(
    is_read_credit,
    available_credits,
    VecInit(Seq.tabulate(wTL / wAXI) { m =>
      tl.d.bits.data((m + 1) * 8 * wAXI - 1, m * 8 * wAXI)
    })(mux_sel)
  )
  axi.r.valid := tl.d.valid || is_read_credit
  axi.r.bits.resp := 0.U

  when(axi.ar.fire) {
    mux_sel := shamt
    idle := false.B
  }
  tl.a.bits := tle.Get(fromSource = 0.U, toAddress = aligned_addr, lgSize = logWTL.U)._2
  tl.a.valid := idle && axi.ar.valid
  tl.d.ready := !idle
  axi.ar.valid && !is_read_credit
}