package systolic.chisel
import chisel3._
import chisel3.util._

class ProcessingElement extends chisel3.Module {
  val io = IO(new Bundle {
    val wgt = Input(UInt(16.W))
    val wgt_valid = Input(Bool())
    val act = Input(UInt(16.W))
    val act_valid = Input(Bool())

    val accumulator_shift = Input(UInt(16.W))
    val rst_output = Input(Bool())
    val shift_out = Input(Bool())

    val accumulator = Output(UInt(16.W))
    val wgt_out = Output(UInt(16.W))
    val wgt_valid_out = Output(Bool())
    val act_out = Output(UInt(16.W))
    val act_valid_out = Output(Bool())
  })

  val accumulator = Reg(UInt(16.W))
  val wgt_out = Reg(UInt(16.W))
  val wgt_valid_out = Reg(Bool())
  val act_out = Reg(UInt(16.W))
  val act_valid_out = Reg(Bool())
  io.accumulator := accumulator
  io.wgt_out := wgt_out
  io.wgt_valid_out := wgt_valid_out
  io.act_out := act_out
  io.act_valid_out := act_valid_out

  val wgt_f = io.wgt(14, 0)
  val act_f = io.act(14, 0)
  val wgt_s = io.wgt(15)
  val act_s = io.wgt(15)

  val product = wgt_f * act_f
  val product_f = product(23, 8)
  val product_s = act_s ^ wgt_s;

  val accumulator_f = accumulator(14, 0)
  val accumulator_s = accumulator(15)

  val opp_sign = product_s ^ accumulator_s
  val adj_product_f = Mux(opp_sign, (~product_f)+1.U, product_f)
  val addition = accumulator_f + adj_product_f

  val oflow = addition(14)
  val n_acc_s = accumulator_s ^ oflow

  val n_acc_f = (addition ^ VecInit(Seq.fill(16)(oflow)).asUInt) + oflow
  val updated_accumulator = Cat(n_acc_s, n_acc_f)

  when (io.rst_output) {
    accumulator := 0.U
    act_valid_out := 0.U
    wgt_valid_out := 0.U
  }.otherwise {
    when (io.shift_out) {
      accumulator <= io.accumulator_shift
    }.otherwise {
      wgt_valid_out := io.wgt_valid
      act_valid_out := io.act_valid
      when (io.wgt_valid && io.act_valid) {
        accumulator := updated_accumulator
      }
    }
  }
  wgt_out := io.wgt
  act_out := io.act
}