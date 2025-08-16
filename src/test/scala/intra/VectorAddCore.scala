package intra

import chisel3._
import chisel3.util._
import beethoven._
import beethoven.common._
import chipsalliance.rocketchip.config.Parameters

class VecAddCoreCommand()(implicit p: Parameters) extends AccelCommand("vector_add") {
  val vec_a_addr = Address()
  val vec_b_addr = Address()
  val vec_out_addr = Address()
  val vector_length = UInt(32.W)
}

//noinspection TypeAnnotation,ScalaWeakerAccess
class VectorAddCore()(implicit p: Parameters) extends AcceleratorCore {
  val my_io = BeethovenIO(new VecAddCoreCommand(), new EmptyAccelResponse())
  println(s"core id: $my_io.req.bits.getCoreID")

  val vec_a_reader = getReaderModule("vec_a")
  val vec_b_reader = getReaderModule("vec_b")
  val vec_out_writer = getWriterModule("vec_out")

  val vec_length_bytes = my_io.req.bits.vector_length * 4.U

  // from our previously defined module
  val dut = Module(new basic.VectorAdd())

  /**
   * provide sane default values
   */
  my_io.req.ready := false.B
  my_io.resp.valid := false.B
  my_io.resp.bits := DontCare
  // .fire is a Chisel-ism for "ready && valid"
  vec_a_reader.requestChannel.valid := my_io.req.fire
  vec_a_reader.requestChannel.bits.addr := my_io.req.bits.vec_a_addr
  vec_a_reader.requestChannel.bits.len := vec_length_bytes

  vec_b_reader.requestChannel.valid := my_io.req.fire
  vec_b_reader.requestChannel.bits.addr := my_io.req.bits.vec_b_addr
  vec_b_reader.requestChannel.bits.len := vec_length_bytes

  vec_out_writer.requestChannel.valid := my_io.req.fire
  vec_out_writer.requestChannel.bits.addr := my_io.req.bits.vec_out_addr
  vec_out_writer.requestChannel.bits.len := vec_length_bytes

  vec_a_reader.dataChannel.data.ready := false.B
  vec_b_reader.dataChannel.data.ready := false.B
  vec_out_writer.dataChannel.data.valid := false.B
  vec_out_writer.dataChannel.data.bits := DontCare

  dut.io.vec_a <> vec_a_reader.dataChannel.data
  dut.io.vec_b <> vec_b_reader.dataChannel.data
  dut.io.vec_out <> vec_out_writer.dataChannel.data

  // state machine
  val s_idle :: s_working :: s_finish :: Nil =  Enum(3)
  val state = RegInit(s_idle)


  when (state === s_idle) {
    my_io.req.ready := vec_a_reader.requestChannel.ready &&
      vec_b_reader.requestChannel.ready &&
      vec_out_writer.requestChannel.ready
    when (my_io.req.fire) {
      state := s_working
    }
  }.elsewhen(state === s_working) {
    // when the writer has finished writing the final datum,
    // isFlushed will be driven high
    when (vec_out_writer.dataChannel.isFlushed) {
      state := s_finish
    }
  }.otherwise {
    my_io.resp.valid := vec_out_writer.requestChannel.ready
    when (my_io.resp.fire) {
      state := s_idle
    }
  }
}
