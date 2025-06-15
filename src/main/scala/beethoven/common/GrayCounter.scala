package beethoven.common

import chisel3._

import scala.annotation.tailrec

class GrayCounter(N: Int) extends Module {
  val countWidth = CLog2Up(N)
  val io = IO(new Bundle {
    val incr = Input(Bool())
    val count = Output(UInt(countWidth.W))
  })

  val cntr = RegInit(0.U(countWidth.W))

  when (io.incr) {
    cntr := cntr + 1.U
  }
  io.count := GrayCounter.toGray(cntr)
}

object GrayCounter {
  def toBinary(a: UInt): UInt = {
    @tailrec
    def h(b: UInt, acc: UInt): UInt = {
      if (b.getWidth == 1) acc ^ b
      else h(b.tail(1), acc ^ b)
    }
    h(a, 0.U(a.getWidth))
  }

  def toGray(a: UInt): UInt = a ^ a.tail(1)
}
