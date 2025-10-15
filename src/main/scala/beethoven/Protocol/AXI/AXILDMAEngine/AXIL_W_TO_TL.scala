package beethoven.Protocol.AXI.AXILDMAEngine
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import beethoven.common.CLog2Up
import org.chipsalliance.diplomacy.tilelink._

class AXIL_W_TO_TL(
    wAXI: Int,
    tbp: TLBundleParameters,
    tle: TLEdgeOut,
    source_width: Int,
    credit_width: Int
) extends Module {
  val tl = IO(TLBundle(tbp))
  val wTL = tl.d.bits.data.getWidth / 8
  val logWTL = CLog2Up(wTL)
  val logWAXI = CLog2Up(wAXI)
  val addrW = tl.a.bits.address.getWidth

  val axi = IO(new Bundle {
    val w = Flipped(Decoupled(new Bundle {
      val data = UInt((wAXI * 8).W)
      val strb = UInt(wAXI.W)
    }))
    val aw = Flipped(Decoupled(new Bundle {
      val addr = UInt(addrW.W)
    }))

    val b = Decoupled(new Bundle {
      val resp = UInt(2.W)
    })
  })

  val max_credit = (1 << credit_width) - 1

  // credits are the number of spaces in our buffer (hopefully implemented as BRAM-backed FIFO)
  val credits_reg = RegInit(UInt(credit_width.W), max_credit.U)
  val credits = IO(credits_reg.cloneType)

  val source_io = IO(Flipped(Decoupled(UInt(source_width.W))))
  source_io.ready := tl.a.fire
  credits := credits_reg
  val wQ = Queue(axi.w, max_credit + 1)
  val awQ = Queue(axi.aw, max_credit + 1)
  val w_in, aw_in = RegInit(UInt(credit_width.W), 0.U)

  // issue responses immediately to free up resources in the shell
  axi.b.valid := w_in > 0.U && aw_in > 0.U
  axi.b.bits.resp := 0.U
  when(axi.w.fire && !axi.b.fire) {
    w_in := w_in + 1.U
  }.elsewhen(!axi.w.fire && axi.b.fire) {
    w_in := w_in - 1.U
  }
  when(axi.aw.fire && !axi.b.fire) {
    aw_in := aw_in + 1.U
  }.elsewhen(!axi.aw.fire && axi.b.fire) {
    aw_in := aw_in - 1.U
  }

  // when can we emit a write on TL
  val can_advance_write_machine = awQ.valid && wQ.valid && source_io.valid
  awQ.ready := can_advance_write_machine && tl.a.ready
  wQ.ready := can_advance_write_machine && tl.a.ready
  tl.a.valid := can_advance_write_machine

  // credits determine how much space in the buffer we have
  // we can consume buffer space, but should never overrun
  when(awQ.fire && !axi.aw.fire) {
    credits_reg := credits_reg + 1.U
  }.elsewhen(axi.aw.fire && !awQ.fire) {
    credits_reg := credits_reg - 1.U
  }
  val shamt: UInt = axi.aw.bits.addr(logWTL - 1, logWAXI)
  val mask = (if (wAXI == wTL) {
                BigInt("1" * wAXI, 2).U
              } else if (wAXI < wTL) {
                BigInt("1" * wAXI, 2).U << (wAXI.U * shamt)
              } else {
                throw new Exception("Cannot have an AXIL width greater than TL width")
              })

  tl.a.bits := tle
    .Put(
      fromSource = source_io.bits,
      toAddress = Cat(awQ.bits.addr(addrW - 1, logWTL), 0.U(logWTL.W)),
      lgSize = logWTL.U,
      data = wQ.bits.data << (8.U * shamt),
      mask = mask
    )
    ._2
  tl.d.ready := true.B
}
