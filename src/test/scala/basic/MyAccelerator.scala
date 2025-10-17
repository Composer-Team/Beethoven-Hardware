package basic

import beethoven._
import beethoven.common._
import org.chipsalliance.cde.config._
import chisel3._
import chisel3.util._

class MyAccelerator(dWidthBytes: Int)(implicit p: Parameters) extends AcceleratorCore {
  val ReaderModuleChannel(vec_in_request, vec_in_data) = getReaderModule(
    "vec_in"
  )
  val WriterModuleChannel(vec_out_request, vec_out_data) = getWriterModule(
    "vec_out"
  )
  vec_in_request.valid := false.B
  vec_out_request.valid := false.B
  vec_in_request.bits := DontCare
  vec_out_request.bits := DontCare
  println("")
  val io = BeethovenIO(
    new AccelCommand("my_accel") {
      val addend = UInt(dWidthBytes.W)
      val vec_addr = Address()
      val n_eles = UInt(20.W)
    },
    EmptyAccelResponse()
  )
  io.req.ready := false.B
  io.resp.valid := false.B
  val addendReg = Reg(UInt(32.W))
  val s_idle :: s_active :: s_response :: Nil = Enum(3)
  val state = RegInit(s_idle)
  io.req.ready := false.B
  when(state === s_idle) {
    io.req.ready := vec_in_request.ready && vec_out_request.ready
    when(io.req.fire) {
      vec_in_request.valid := true.B
      vec_out_request.valid := true.B
      addendReg := io.req.bits.addend
      state := s_active
    }
  }.elsewhen(state === s_active) {
    when (vec_out_request.ready && vec_out_data.isFlushed) {
      state := s_response
    }
  }.elsewhen(state === s_response) {
    io.resp.valid := true.B
    when (io.resp.fire) {
      state := s_idle
    }
  }

  val write_len_bytes = Cat(io.req.bits.n_eles, 0.U(2.W))

  vec_in_request.valid := io.req.valid
  vec_in_request.bits.addr := io.req.bits.vec_addr
  vec_in_request.bits.len := write_len_bytes

  vec_out_request.valid := io.req.valid
  vec_out_request.bits.addr := io.req.bits.vec_addr
  vec_out_request.bits.len := write_len_bytes
  // split vector into 32b chunks and add addend to it

  vec_in_data.data.ready := vec_out_data.data.ready
  vec_out_data.data.valid := vec_in_data.data.valid
  vec_out_data.data.bits := vec_in_data.data.bits + addendReg
}
