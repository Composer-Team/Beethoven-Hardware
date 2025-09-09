package beethoven.Protocol.RoCC.Helpers

import chisel3._
import chisel3.util._
import beethoven._
import freechips.rocketchip.amba.axi4._
import chipsalliance.rocketchip.config._
import beethoven.Platforms._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class Permissions(readable: Boolean, writeable: Boolean)

object ReadOnly extends Permissions(true, false)

object WriteOnly extends Permissions(false, true)

object ReadWrite extends Permissions(true, true)

abstract class MCRMapEntry {
  def name: String

  def permissions: Permissions
}

case class DecoupledSinkEntry(node: DecoupledIO[UInt], name: String)
    extends MCRMapEntry {
  val permissions = WriteOnly
}

case class DecoupledSourceEntry(node: DecoupledIO[UInt], name: String)
    extends MCRMapEntry {
  val permissions = ReadOnly
}

case class RegisterEntry(node: Data, name: String, permissions: Permissions)
    extends MCRMapEntry

class MCRFileMap {
  // DO NOT put the MMIOs in the first page. For unified memory systems this will result in null pointer dereferences
  // not segfaulting
  private val name2addr = mutable.LinkedHashMap[String, Int]()
  private val regList = ArrayBuffer[MCRMapEntry]()

  def allocate(entry: MCRMapEntry): Int = {
    Predef.assert(!name2addr.contains(entry.name), "name already allocated")
    val address = name2addr.size
    name2addr += (entry.name -> address)
    regList.append(entry)
    address
  }

  def lookupAddress(name: String): Option[Int] = name2addr.get(name)

  def numRegs: Int = regList.size

  def bindRegs(mcrIO: MCRIO): Unit = regList.zipWithIndex foreach {
    case (e: DecoupledSinkEntry, addr)   => mcrIO.bindDecoupledSink(e, addr)
    case (e: DecoupledSourceEntry, addr) => mcrIO.bindDecoupledSource(e, addr)
    case (e: RegisterEntry, addr)        => mcrIO.bindReg(e, addr)
  }

  def getCRdef(implicit p: Parameters): Seq[(String, String)] = {
    (regList.zipWithIndex map { case (entry, i) =>
      val addr = i << log2Up(p(PlatformKey).frontBusBeatBytes)
      require(i < 1024)
      (entry.name.toUpperCase, f"0x${addr.toHexString}")
    }).toSeq
  }
}

class MCRIO(numCRs: Int)(implicit p: Parameters)
    extends ParameterizedBundle()(p) {
  val read =
    Vec(numCRs, Flipped(Decoupled(UInt((p(CmdRespBusWidthBytes) * 8).W))))
  val write = Vec(numCRs, Decoupled(UInt((p(CmdRespBusWidthBytes) * 8).W)))
  val wstrb = Output(UInt(p(CmdRespBusWidthBytes).W))

  def bindReg(reg: RegisterEntry, addr: Int): Unit = {
    if (reg.permissions.writeable) {
      when(write(addr).valid) {
        reg.node := write(addr).bits
      }
    } else {
      assert(write(addr).valid != true.B, s"Register ${reg.name} is read only")
    }

    if (reg.permissions.readable) {
      read(addr).bits := reg.node
    } else {
      assert(read(addr).ready === false.B, "Register ${reg.name} is write only")
    }

    read(addr).valid := true.B
    write(addr).ready := true.B
  }

  def bindDecoupledSink(channel: DecoupledSinkEntry, addr: Int): Unit = {
    channel.node <> write(addr)
    assert(
      read(addr).ready === false.B,
      "Can only write to this decoupled sink"
    )
  }

  def bindDecoupledSource(channel: DecoupledSourceEntry, addr: Int): Unit = {
    read(addr) <> channel.node
    assert(
      write(addr).valid =/= true.B,
      "Can only read from this decoupled source"
    )
  }

}

trait MCRFile {
  def getMCRIO: MCRIO
}

class Protocol2RoccWidget(numRegs: Int)(implicit p: Parameters)
    extends LazyModule
    with MCRFile {
  require(
    (platform.frontBusBaseAddress & 0x3ffL) == 0,
    "Platform: The defined front bus address must be aligned to 0x400 (1KB)"
  )
  val node = AXI4SlaveNode(portParams =
    Seq(
      AXI4SlavePortParameters(
        slaves = Seq(
          AXI4SlaveParameters(
            address = Seq(
              AddressSet(
                platform.frontBusBaseAddress,
                platform.frontBusAddressMask
              )
            ),
            supportsRead = TransferSizes(platform.frontBusBeatBytes),
            supportsWrite = TransferSizes(platform.frontBusBeatBytes)
          )
        ),
        beatBytes = platform.frontBusBeatBytes
      )
    )
  )
  lazy val module = new MCRFileModuleAXI(this, numRegs)

  override def getMCRIO: MCRIO = module.io.mcr
}

class MCRFileModuleAXI(outer: Protocol2RoccWidget, numRegs: Int)(implicit
    p: Parameters
) extends LazyModuleImp(outer) {

  val io = IO(new Bundle {
    val mcr = new MCRIO(numRegs)
  })

  val logNumRegs = log2Up(numRegs)
  val (in, edge) = outer.node.in(0)

  val s_idle :: s_read :: s_write :: s_write_response :: Nil = Enum(4)
  val state = RegInit(s_idle)

  val address = Reg(UInt(logNumRegs.W))
  val writeData = Reg(UInt(32.W))
  val readData = Reg(UInt((platform.frontBusBeatBytes * 8).W))

  in.r.valid := false.B
  in.r.bits.data := readData
  in.r.bits.id := 0.U
  in.r.bits.last := false.B
  in.w.ready := false.B
  in.b.valid := false.B
  in.b.bits := DontCare

  // initialize read/write value wires
  io.mcr.read.foreach { rChannel =>
    rChannel.ready := false.B
  }
  io.mcr.write.foreach { wChannel =>
    wChannel.bits := in.w.bits.data(31, 0)
    wChannel.valid := false.B
  }

  // WRITE
  val write_machine = {
    val s_idle :: s_write :: s_drain :: s_response :: Nil = Enum(4)
    val state = RegInit(s_idle)
    val addr = Reg(UInt(logNumRegs.W))
    val len = Reg(in.aw.bits.len.cloneType)
    val id = Reg(in.aw.bits.id.cloneType)
    in.aw.ready := state === s_idle
    when(in.w.fire) {
      len := len - 1.U
    }
    when(state === s_idle) {
      val addr_skip = log2Up(platform.frontBusBeatBytes)
      addr := in.aw.bits.addr(logNumRegs + addr_skip - 1, addr_skip)
      len := in.aw.bits.len
      when(in.aw.fire) {
        state := s_write
        id := in.aw.bits.id
      }
    }.elsewhen(state === s_write) {
      in.w.ready := true.B
      io.mcr.write(addr).valid := in.w.valid
      when(in.w.fire) {
        when(len === 0.U) {
          state := s_response
        }.otherwise {
          state := s_drain
        }
      }
    }.elsewhen(state === s_drain) {
      in.w.ready := true.B
      when(in.w.fire && len === 0.U) {
        state := s_response
      }
    }.otherwise {
      in.b.valid := true.B
      in.b.bits.resp := 0.U
      in.b.bits.id := id
      when(in.b.fire) {
        state := s_idle
      }
    }
  }

  val read_machine = {
    val s_idle :: s_read :: s_drain :: Nil = Enum(3)
    val state = RegInit(s_idle)
    in.ar.ready := state === s_idle
    val addr = Reg(UInt(logNumRegs.W))
    val len = Reg(in.ar.bits.len.cloneType)
    val id = Reg(in.ar.bits.id.cloneType)
    in.r.bits.id := id
    in.r.bits.last := len === 0.U
    in.r.bits.resp := 0.U
    in.r.bits.data := 0.U

    when(in.r.fire) {
      len := len - 1.U
    }
    when(state === s_idle) {
      in.ar.ready := true.B
      val addr_skip = log2Up(platform.frontBusBeatBytes)
      addr := in.ar.bits.addr(logNumRegs + addr_skip - 1, addr_skip)
      len := in.ar.bits.len
      id := in.ar.bits.id
      when(in.ar.fire) {
        state := s_read
      }
    }.elsewhen(state === s_read) {
      in.r.valid := true.B
      if (platform.frontBusBeatBytes == 4) {
        in.r.bits.data := io.mcr.read(addr).bits
      } else {
        in.r.bits.data := Cat(
          0.U((8 * (platform.frontBusBeatBytes - 4)).W),
          io.mcr.read(addr).bits
        )
      }
      io.mcr.read(addr).ready := in.r.ready
      when(in.r.fire) {
        when(len === 0.U) {
          state := s_idle
        }.otherwise {
          state := s_drain
        }
      }
    }.elsewhen(state === s_drain) {
      in.r.valid := true.B
      in.r.bits.data := 0xabcdabcdL.U
      when(len === 0.U && in.r.fire) {
        state := s_idle
      }
    }
  }
}
