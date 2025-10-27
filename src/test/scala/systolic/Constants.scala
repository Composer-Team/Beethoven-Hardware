package systolic

import chisel3.util.isPow2

object Constants {
  val systolic_array_dim = 8

  val data_width_bits = 16
  val data_width_bytes = data_width_bits / 8
  val int_bits = 7
  val frac_bits = 8
  
  require(int_bits + frac_bits + 1 == data_width_bits)
  require(isPow2(data_width_bits))
}