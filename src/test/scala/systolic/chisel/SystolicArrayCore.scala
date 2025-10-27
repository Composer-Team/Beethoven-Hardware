package systolic.chisel
import beethoven._
import beethoven.common._
import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import systolic.Constants.data_width_bytes

class SystolicArrayCore(dim: Int)(implicit p: Parameters) extends AcceleratorCore {
  val io = BeethovenIO(new SystolicArrayCmd(), EmptyAccelResponse())
  val ReaderModuleChannel(weights_req, weights) = getReaderModule("weights")
  val ReaderModuleChannel(activations_req, activations) = getReaderModule("activations")
  val WriterModuleChannel(output_req, output) = getWriterModule("vec_out")

  val cmd_fire = io.req.fire
  output_req.valid := cmd_fire
  weights_req.valid := cmd_fire
  activations_req.valid := cmd_fire

  output_req.bits.len := data_width_bytes.U * (dim * dim).U
  weights_req.bits.len := data_width_bytes.U * dim.U * io.req.bits.inner_dimension
  activations_req.bits.len := data_width_bytes.U * dim.U * io.req.bits.inner_dimension

  weights_req.bits.addr := Address(io.req.bits.wgt_addr)
  activations_req.bits.addr := Address(io.req.bits.act_addr)
  output_req.bits.addr := Address(io.req.bits.out_addr)

  val s_idle :: s_go :: s_flush :: s_response :: Nil = Enum(4)
  val state = RegInit(s_idle)

  io.req.ready := state === s_idle && weights_req.ready && activations_req.ready && output_req.ready
  io.resp.valid := state === s_response

  val sa_idle = Wire(Bool())
  val sa = Module(new SystolicArray())
  sa.io.act_in := activations.data.bits
  activations.data.ready := sa.io.act_ready
  sa.io.act_valid := activations.data.valid

  sa.io.wgt_in := weights.data.bits
  weights.data.ready := sa.io.wgt_ready
  sa.io.wgt_valid := weights.data.valid

  output.data.valid := sa.io.accumulator_out_valid
  sa.io.accumulator_out_ready := output.data.ready
  output.data.bits := sa.io.accumulator_out

  sa.io.ctrl_start_matmul := cmd_fire
  sa.io.ctrl_inner_dimension := io.req.bits.inner_dimension
  sa_idle := sa.io.ctrl_start_ready

  when(state === s_idle) {
    when(cmd_fire) {
      state := s_go
    }
  }.elsewhen(state === s_go) {
    when(sa_idle) {
      state := s_flush
    }
  }.elsewhen(state === s_flush) {
    when(output_req.ready && output.isFlushed) {
      state := s_response
    }
  }.elsewhen(state === s_response) {
    when(io.resp.ready) {
      state := s_idle
    }
  }
}
