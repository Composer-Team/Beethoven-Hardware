package systolic.chisel

import chisel3._
import chisel3.util._
import beethoven.common.ShiftReg
import org.chipsalliance.cde.config.Parameters
import beethoven.common.ShiftRegEnable
import systolic.Constants._

class SystolicArray(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val act_in = Input(UInt((systolic_array_dim * data_width_bits).W))
    val act_valid = Input(Bool())
    val act_ready = Output(Bool())

    val wgt_in = Input(UInt((systolic_array_dim * data_width_bits).W))
    val wgt_valid = Input(Bool())
    val wgt_ready = Output(Bool())

    val accumulator_out = Output(UInt((systolic_array_dim * data_width_bits).W))
    val accumulator_out_valid = Output(Bool())
    val accumulator_out_ready = Input(Bool())

    val ctrl_start_matmul = Input(Bool())
    val ctrl_start_ready = Output(Bool())
    val ctrl_inner_dimension = Input(UInt(20.W))
  })
  val PEs = Seq.fill(systolic_array_dim, systolic_array_dim)(Module(new ProcessingElement()))

  val s_idle :: s_go :: s_drain :: s_shift :: Nil = Enum(4)
  val state = RegInit(s_idle)

  val shift_out = state === s_shift
  io.accumulator_out_valid := state === s_shift
  io.ctrl_start_ready := state === s_idle

  io.wgt_ready := io.act_valid && io.wgt_valid
  io.act_ready := io.act_valid && io.wgt_valid

  val inner_dimension_ctr = Reg(UInt(20.W))
  val n_inner_dimension_ctr = inner_dimension_ctr - 1.U
  val can_increment_inputs = (io.act_valid && io.wgt_valid) || state =/= s_go
  io.accumulator_out := Cat(PEs.map(a => a(0).io.accumulator).reverse)
  (0 until systolic_array_dim).foreach { row =>
    val act_for_row = io.act_in((row + 1) * data_width_bits - 1, row * data_width_bits)
    (0 until systolic_array_dim).foreach { col =>
      val wgt_for_col = io.wgt_in((col + 1) * data_width_bits - 1, col * data_width_bits)
      val curr = PEs(row)(col)
      curr.io.rst_output := io.ctrl_start_matmul
      curr.io.shift_out := shift_out && io.accumulator_out_ready
      curr.io.wgt := (row match {
        case 0 => ShiftRegEnable(wgt_for_col, col, can_increment_inputs, clock)
        case _ => PEs(row - 1)(col).io.wgt_out
      })
      curr.io.wgt_valid := (row match {
        case 0 => ShiftRegEnable(io.wgt_valid, col, can_increment_inputs, clock)
        case _ => PEs(row - 1)(col).io.wgt_valid_out
      })
      curr.io.act := (col match {
        case 0 => ShiftRegEnable(act_for_row, row, can_increment_inputs, clock)
        case _ => PEs(row)(col - 1).io.act_out
      })
      curr.io.act_valid := (col match {
        case 0 => ShiftRegEnable(io.act_valid, row, can_increment_inputs, clock)
        case _ => PEs(row)(col - 1).io.act_valid_out
      })
      curr.io.accumulator_shift := (if (col == systolic_array_dim - 1) {
                                      0.U
                                    } else {
                                      PEs(row)(col + 1).io.accumulator
                                    })
    }
  }

  when(state === s_idle) {
    when(io.ctrl_start_matmul) {
      inner_dimension_ctr := io.ctrl_inner_dimension
      state := s_go
    }
  }.elsewhen(state === s_go) {
    when(can_increment_inputs) {
      when(n_inner_dimension_ctr === 0.U) {
        inner_dimension_ctr := (2 * systolic_array_dim).U
        state := s_drain
      }.otherwise {
        inner_dimension_ctr := n_inner_dimension_ctr
      }
    }
  }.elsewhen(state === s_drain) {
    inner_dimension_ctr := n_inner_dimension_ctr
    when(n_inner_dimension_ctr === 0.U) {
      inner_dimension_ctr := systolic_array_dim.U
      state := s_shift
    }
  }.otherwise {
    when(io.accumulator_out_ready) {
      inner_dimension_ctr := n_inner_dimension_ctr
      when(n_inner_dimension_ctr === 0.U) {
        state := s_idle
      }
    }
  }
}
