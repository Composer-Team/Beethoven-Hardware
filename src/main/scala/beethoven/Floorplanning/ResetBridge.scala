package beethoven.Floorplanning

import chisel3._
import beethoven.common.ShiftReg
import org.chipsalliance.cde.config.Parameters

object ResetBridge {
  def apply[T <: Reset](dut: T, bridgeDelay: Int)(implicit p: Parameters): T = {
    val bridge = Module(new ResetBridge(dut, bridgeDelay))
    bridge.io.areset <> dut
    bridge.io.dut_areset
  }
  def apply[T <: Reset](dut: T, clock: Clock, bridgeDelay: Int)(implicit
      p: Parameters
  ): T = {
    val bridge = Module(new ResetBridge(dut, bridgeDelay))
    bridge.io.areset <> dut
    bridge.io.clock := clock
    bridge.io.dut_areset
  }
}

class ResetBridge[T <: Reset](dut: T, bridgeDelay: Int)(implicit p: Parameters)
    extends RawModule {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val areset = Input(dut.cloneType)
    val dut_areset = Output(dut.cloneType)
  })
  withClockAndReset(io.clock, false.B.asAsyncReset) {
    io.dut_areset := ShiftReg(
      io.areset.asBool,
      bridgeDelay,
      io.clock,
      a => a.asBool
    )
  }
}
