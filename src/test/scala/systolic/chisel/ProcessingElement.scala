package systolic.chisel
import chisel3._
import chisel3.util._
import systolic.Constants._

class ProcessingElement extends chisel3.Module {
  val io = IO(new Bundle {
    val wgt = Input(UInt(data_width_bits.W))
    val wgt_valid = Input(Bool())
    val act = Input(UInt(data_width_bits.W))
    val act_valid = Input(Bool())

    val accumulator_shift = Input(UInt(data_width_bits.W))
    val rst_output = Input(Bool())
    val shift_out = Input(Bool())

    val accumulator = Output(UInt(data_width_bits.W))
    val wgt_out = Output(UInt(data_width_bits.W))
    val wgt_valid_out = Output(Bool())
    val act_out = Output(UInt(data_width_bits.W))
    val act_valid_out = Output(Bool())
  })

  val accumulator = Reg(UInt(data_width_bits.W))
  val wgt_out = Reg(UInt(data_width_bits.W))
  val wgt_valid_out = Reg(Bool())
  val act_out = Reg(UInt(data_width_bits.W))
  val act_valid_out = Reg(Bool())
  io.accumulator := accumulator
  io.wgt_out := wgt_out
  io.wgt_valid_out := wgt_valid_out
  io.act_out := act_out
  io.act_valid_out := act_valid_out

  val wgt_f = io.wgt.tail(1)
  val act_f = io.act.tail(1)
  val wgt_s = io.wgt.head(1).asBool
  val act_s = io.act.head(1).asBool

  val product = wgt_f * act_f
  val product_f = product(frac_bits * 2 + int_bits - 1, frac_bits)
  val product_s = act_s ^ wgt_s;

  val accumulator_f = accumulator.tail(1)
  val accumulator_s = accumulator.head(1).asBool

  val opp_sign = product_s ^ accumulator_s
  val adj_product_f = Mux(opp_sign, (~product_f) + 1.U, product_f)
  val addition = accumulator_f + adj_product_f

  val oflow = addition.tail(1).head(1).asBool
  val n_acc_s = accumulator_s ^ oflow

  val n_acc_f = (addition ^ VecInit(Seq.fill(data_width_bits - 1)(oflow)).asUInt) + oflow
  val updated_accumulator = Cat(n_acc_s, n_acc_f)

  when(io.rst_output) {
    accumulator := 0.U
    act_valid_out := 0.U
    wgt_valid_out := 0.U
  }.otherwise {
    when(io.shift_out) {
      accumulator := io.accumulator_shift
    }.otherwise {
      when(io.wgt_valid && io.act_valid) {
        accumulator := updated_accumulator
      }
    }
  }
  wgt_valid_out := io.wgt_valid
  act_valid_out := io.act_valid
  wgt_out := io.wgt
  act_out := io.act

}

// object ProcessingElement {
//   final def main(args: Array[String]): Unit = {
//     println(emitVerilog(new ProcessingElement, annotations = Seq(FirtoolOption("--help"))))
//   }
// }
