package beethoven.Protocol.RoCC.Helpers

import beethoven.Generation.CppGeneration
import beethoven.Platforms.PlatformKey
import beethoven.platform
import org.chipsalliance.cde.config._
import chisel3._
import chisel3.util._
import org.chipsalliance.diplomacy.amba.axi4.AXI4IdentityNode
import org.chipsalliance.diplomacy.{LazyModule, LazyModuleImp}
import os.makeDir.all

class FrontBusWidget(implicit p: Parameters) extends LazyModule {
  val node = AXI4IdentityNode()
  val maxRegisters = 16
  val crFile = LazyModule(new Protocol2RoccWidget(maxRegisters))
  crFile.node := node
  override lazy val module = new AXILWidgetModule(this)
}

class AXILWidgetModule(outer: FrontBusWidget) extends LazyModuleImp(outer) {

  val io = IO(new Bundle {
    val cmds = Decoupled(UInt(32.W))
    val resp = Flipped(Decoupled(UInt(32.W)))

    val cache_prot =
      if (platform.hasDebugAXICACHEPROT) Some(Output(UInt(7.W))) else None
  })

  val roccCmdFifo = Module(new Queue(UInt(32.W), outer.maxRegisters))
  val roccRespFifo = Module(new Queue(UInt(32.W), outer.maxRegisters))
  val mcrio = outer.crFile.module.io.mcr

  var allocated = 0

  def genPulsedValid(name: String): Bool = {
    require(allocated < outer.maxRegisters)
    val state = RegInit(false.B)
    mcrio.write(allocated).ready := true.B
    when (mcrio.write(allocated).fire) {
      state := mcrio.write(allocated).bits(0)
    }.otherwise {
      state := false.B
    }
    mcrio.read(allocated).valid := true.B
    mcrio.read(allocated).bits := 0xFAFABCBCL.U
    val addr = allocated << log2Up(p(PlatformKey).frontBusBeatBytes)
    CppGeneration.addPreprocessorDefinition(name, addr)
    allocated += 1
    state
  }

  def genRO[T <: Data](name: String, src: T): Unit = {
    require(allocated < outer.maxRegisters)
    val addr = allocated << log2Up(p(PlatformKey).frontBusBeatBytes)
    CppGeneration.addPreprocessorDefinition(name, addr)
    mcrio.read(allocated).bits := src
    mcrio.read(allocated).valid := true.B
    mcrio.write(allocated).ready := true.B
    allocated += 1
  }

  def genWO[T <: Data](name: String, init: UInt): UInt = {    
    require(allocated < outer.maxRegisters)

    val addr = allocated << log2Up(p(PlatformKey).frontBusBeatBytes)
    CppGeneration.addPreprocessorDefinition(name, addr)

    val state = RegInit(UInt(32.W), init)
    mcrio.write(allocated).ready := true.B
    when(mcrio.write(allocated).valid) {
      state := mcrio.write(allocated).bits
    }
    mcrio.read(allocated).bits := 0xFAFABCBCL.U
    mcrio.read(allocated).valid := true.B
    allocated += 1
    state
  }

  roccCmdFifo.io.enq.valid := genPulsedValid("CMD_VALID")
  roccCmdFifo.io.enq.bits := genWO("CMD_BITS", 0.U(32.W))
  genRO("CMD_READY", roccCmdFifo.io.enq.ready)

  genRO("RESP_VALID", roccRespFifo.io.deq.valid)
  genRO("RESP_BITS", roccRespFifo.io.deq.bits)
  roccRespFifo.io.deq.ready := genPulsedValid("RESP_READY")

  genRO("AXIL_DEBUG", 0xdeadcafeL.U(32.W))

  if (platform.hasDebugAXICACHEPROT) {
    val prot_cache = Wire(UInt(7.W))
    prot_cache := genWO("CACHEPROT", 0x7a.U(7.W))
    io.cache_prot.get := prot_cache
  }

  for (i <- allocated until outer.maxRegisters) {
    mcrio.read(i) := DontCare
    mcrio.write(i) := DontCare
  }

  io.cmds <> roccCmdFifo.io.deq
  roccRespFifo.io.enq <> io.resp

}
