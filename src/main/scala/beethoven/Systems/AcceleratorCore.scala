package beethoven

import beethoven.AcceleratorCore._
import beethoven.BeethovenParams.CoreIDLengthKey
import beethoven.IntraCoreMemoryPortInConfig._
import beethoven.MemoryStreams._
import beethoven.Protocol.RoCC._
import beethoven.Protocol.tilelink.MultiBeatCommandEmitter
import beethoven.Systems._
import beethoven.common._
import beethoven._
import org.chipsalliance.cde.config._
import chisel3._
import chisel3.util._
import org.chipsalliance.diplomacy.tilelink._

import scala.language.implicitConversions
import MemoryStreams.Writers.WriterDataChannelIO

class CustomIO[T1 <: AccelCommand, T2 <: AccelResponse](
    bundleIn: T1,
    bundleOut: T2,
    respExists: Boolean
) extends Bundle {
  val _req: DecoupledIO[T1] = DecoupledIO(bundleIn)
  val _resp: Option[DecoupledIO[T2]] =
    if (respExists) Some(Flipped(DecoupledIO(bundleOut))) else None

  def req: DecoupledIO[T1] = _req

  def resp: DecoupledIO[T2] = if (respExists) _resp.get
  else
    throw new Exception(
      "Tried to access the response bundle for a command that doesn't exist.\n" +
        s"Check the definition of your IO for this command/response port (${bundleIn.commandName})"
    )
}

private[beethoven] object CustomCommandUsage extends Enumeration {
  // noinspection ScalaUnusedSymbol
  type custom_usage = Value
  val unused, default, custom = Value
}

class DataChannelIO(dWidth: Int) extends Bundle {
  val data = Decoupled(UInt(dWidth.W))
  val in_progress = Output(Bool())
}

object AcceleratorCore {
  private var systemOpCodeMap = List[(String, String, Int)]()

  private var commandExpectsResponse: List[(String, String, Boolean)] =
    List.empty

  implicit def addressToUInt(addr: Address): UInt = addr.address
}

object OuterKey extends Field[AcceleratorSystem]

class AcceleratorCore(implicit p: Parameters) extends Module {
  val outer: AcceleratorSystem = p(OuterKey)
  val io_declaration = IO(Flipped(new RoccExchange))
  io_declaration.resp.valid := false.B
  io_declaration.resp.bits := DontCare
  io_declaration.req.ready := false.B
  private[beethoven] var using_custom = CustomCommandUsage.unused

  def getIntraCoreMemOut(name: String): Seq[MemWritePort] = {
    val (params, match_params) =
      try {
        val q = outer.intraCoreMemMasters.find(_._1.name == name).get
        val r = p(AcceleratorSystems)
          .find(_.name == q._1.toSystem)
          .get
          .memoryChannelConfig
          .find(_.name == q._1.toMemoryPort)
          .get
          .asInstanceOf[IntraCoreMemoryPortInConfig]
        (q, r)
      } catch {
        case e: Exception =>
          System.err.println(
            "You may be trying to access a intra core mem port by the wrong name. Check your config."
          )
          throw e
      }
    val ports = intra_mem_outs(params._1)

    ports.zipWithIndex.map { case (port: TLBundle, channelIdx) =>
      val w = Wire(
        new MemWritePort(
          getCommMemSpaceBits(),
          port.params.dataBits,
          canSelectCore = has_core_select(match_params.communicationDegree),
          canSelectChannel =
            has_channel_select(match_params.communicationDegree)
        )
      )
      w.suggestName(s"intraCoreWritePort_for$name" + "_ch" + channelIdx)
      port.a.valid := w.valid
      w.ready := port.a.ready
      port.a.bits.source := 0.U
      port.a.bits.address := getCommMemAddress(
        params._1.toSystem,
        w.bits.core.getOrElse(0),
        params._1.toMemoryPort,
        w.bits.channel.getOrElse(0),
        w.bits.addr,
        CLog2Up(port.params.dataBits / 8)
      )
      port.a.bits.size := CLog2Up(port.params.dataBits / 8).U
      port.a.bits.data := w.bits.data
      port.a.bits.mask := BigInt("1" * (match_params.dataWidthBits / 8), 2).U
      port.a.bits.param := DontCare
      port.a.bits.opcode := TLMessages.PutFullData
      port.a.bits.corrupt := false.B
      port.d.ready := true.B
      w
    }
  }

  def getIntraCoreMemIns(name: String): Seq[Seq[ScratchpadDataPort]] = {
    if (!intra_mem_ins.exists(_._1 == name))
      throw new Exception(
        s"Attempting to access intraCoreMem \"$name\" which we can't find in the config."
      )
    intra_mem_ins(name)
  }

  def getIntraCoreMemIn(
      name: String,
      channelIdx: Int
  ): Seq[ScratchpadDataPort] = {
    getIntraCoreMemIns(name)(channelIdx)
  }

  case class ReaderModuleChannel(
      requestChannel: DecoupledIO[ChannelTransactionBundle],
      dataChannel: DataChannelIO
  )

  case class WriterModuleChannel(
      requestChannel: DecoupledIO[ChannelTransactionBundle],
      dataChannel: WriterDataChannelIO
  )

  case class ScratchpadModuleChannel(
      requestChannel: ScratchpadMemReqPort,
      dataChannels: Seq[ScratchpadDataPort]
  )

  def getReaderModule(name: String, idx: Int = 0): ReaderModuleChannel = {
    val a = getReaderModules(name, Some(idx))
    ReaderModuleChannel(a._1(0), a._2(0))
  }

  val read_ios =
    Map.from(
      outer.memParams.filter(_.isInstanceOf[ReadChannelConfig]).map {
        case mp: ReadChannelConfig =>
          (
            mp.name,
            (
              mp,
              (0 until mp.nChannels).map { channelIdx =>
                val io_pair = (
                  IO(Decoupled(new ChannelTransactionBundle)),
                  IO(Flipped(new DataChannelIO(mp.dataBytes * 8)))
                )
                io_pair._1
                  .suggestName(s"readRequest_${mp.name}_channel${channelIdx}")
                io_pair._2
                  .suggestName(s"readData_${mp.name}_channel${channelIdx}")
                io_pair
              }
            )
          )
      }
    )
  val write_ios =
    Map.from(
      outer.memParams.filter(_.isInstanceOf[WriteChannelConfig]).map {
        case mp: WriteChannelConfig =>
          (
            mp.name,
            (
              mp,
              (0 until mp.nChannels).map { channelIdx =>
                val io_pair = (
                  IO(Decoupled(new ChannelTransactionBundle)),
                  IO(Flipped(new WriterDataChannelIO(mp.dataBytes * 8)))
                )
                io_pair._1
                  .suggestName(s"writeRequest_${mp.name}_channel${channelIdx}")
                io_pair._2
                  .suggestName(s"writeData_${mp.name}_channel${channelIdx}")
                io_pair
              }
            )
          )
      }
    )
  val sp_ios =
    Map.from(outer.memParams.filter(_.isInstanceOf[ScratchpadConfig]).map {
      case mp: ScratchpadConfig =>
        (
          mp.name,
          (
            mp,
            (
              {
                val io =
                  IO(Flipped(new ScratchpadMemReqPort(mp.nDatas.intValue())))
                io.suggestName(s"scratchpadRequest_${mp.name}")
                io
              },
              (0 until mp.nPorts).map { ch: Int =>
                val io = IO(
                  Flipped(
                    new ScratchpadDataPort(
                      log2Up(mp.nDatas.intValue()),
                      mp.dataWidthBits.intValue()
                    )
                  )
                )
                io.suggestName(s"scratchpadData_${mp.name}_channel${ch}")
                io
              }
            )
          )
        )
    })

  val intra_mem_ins = Map.from(
    outer.memParams.filter(_.isInstanceOf[IntraCoreMemoryPortInConfig]).map {
      case mp: IntraCoreMemoryPortInConfig =>
        (
          mp.name,
          (0 until mp.nChannels).map { ch: Int =>
            (0 until mp.portsPerChannel) map { port_idx =>
              val io = IO(
                Flipped(
                  new ScratchpadDataPort(
                    log2Up(mp.nDatas.intValue()),
                    mp.dataWidthBits.intValue()
                  )
                )
              )
              io.suggestName(
                s"intra_mem_in_${mp.name}_channel${ch}_port${port_idx}"
              )
              io
            }
          }
        )
    }
  )

  val intra_mem_outs = Map.from(
    outer.memParams.filter(_.isInstanceOf[IntraCoreMemoryPortOutConfig]).map {
      case mp: IntraCoreMemoryPortOutConfig =>
        (
          mp, {
            val matching = p(AcceleratorSystems)
              .find(_.name == mp.toSystem)
              .get
              .memoryChannelConfig
              .find(_.name == mp.toMemoryPort)
              .get
              .asInstanceOf[IntraCoreMemoryPortInConfig]
            (0 until mp.nChannels).map { channel_idx: Int =>
              val io = IO(
                new TLBundle(
                  outer
                    .intraCoreMemMasters(0)
                    ._2(0)
                    .out(0)
                    ._1
                    .params
                    .copy(dataBits = matching.dataWidthBits)
                )
              )
              io.suggestName(
                f"intra_mem_out_to${mp.toSystem}_${mp.toMemoryPort}_ch${channel_idx}"
              )
              io
            }
          }
        )
    }
  )

  /** Declare reader module implementations associated with a certain channel
    * name. Data channel will read out a vector of UInts of dimension (vlen,
    * dataBytes*8 bits)
    *
    * @return
    *   List of transaction information bundles (address and length in bytes)
    *   and then a data channel. For sparse readers, we give back both
    *   interfaces and for non-sparse, addresses are provided through separate
    *   address commands in software.
    */
  def getReaderModules(
      name: String,
      idx: Option[Int] = None
  ): (List[DecoupledIO[ChannelTransactionBundle]], List[DataChannelIO]) = {
    idx match {
      case None =>
        val q = read_ios(name)
        (q._2.map(_._1).toList, q._2.map(_._2).toList)
      case Some(id) =>
        val q = read_ios(name)
        (List(q._2(id)._1), List(q._2(id)._2))
    }
  }

  def getWriterModule(name: String, idx: Int = 0): WriterModuleChannel = {
    val a = getWriterModules(name, Some(idx))
    WriterModuleChannel(a._1(0), a._2(0))
  }

  def getScratchpad(name: String): ScratchpadModuleChannel = {
    val a = sp_ios(name)._2
    a._1.writeback.valid := false.B
    a._1.writeback.bits := DontCare
    ScratchpadModuleChannel(a._1, a._2)
  }

  def getWriterModules(name: String, idx: Option[Int] = None): (
      List[DecoupledIO[ChannelTransactionBundle]],
      List[WriterDataChannelIO]
  ) = {
    idx match {
      case None =>
        val q = write_ios(name)
        (q._2.map(_._1).toList, q._2.map(_._2).toList)
      case Some(id) =>
        val q = write_ios(name)
        (List(q._2(id)._1), List(q._2(id)._2))
    }
  }

  def BeethovenIO[T1 <: AccelCommand](
      bundleIn: T1
  ): CustomIO[T1, AccelRoccUserResponse] = {
    BeethovenIO[T1, AccelRoccUserResponse](bundleIn, InvalidAccelResponse())
  }

  def getSystemID(name: String): UInt =
    p(AcceleratorSystems).indexWhere(_.name == name).U

  private var nCommands = 0

  def BeethovenIO[T1 <: AccelCommand, T2 <: AccelResponse](
      bundleIn: T1,
      bundleOut: T2
  ): CustomIO[T1, T2] = {
    if (using_custom == CustomCommandUsage.default) {
      throw new Exception("Cannot use custom io after using the default io")
    }
    using_custom = CustomCommandUsage.custom
    val opCode =
      if (
        !systemOpCodeMap.exists(a =>
          a._1 == outer.systemParams.name && a._2 == bundleIn.commandName
        )
      ) {
        systemOpCodeMap = (
          outer.systemParams.name,
          bundleIn.commandName,
          nCommands
        ) :: systemOpCodeMap
        nCommands
      } else {
        systemOpCodeMap
          .filter(a =>
            a._1 == outer.systemParams.name && a._2 == bundleIn.commandName
          )(0)
          ._3
      }

    if (
      commandExpectsResponse.exists(a =>
        outer.systemParams.name == a._1 && bundleIn.commandName == a._2
      )
    ) {} else {
      commandExpectsResponse = (
        outer.systemParams.name,
        bundleIn.commandName,
        !bundleOut.isInstanceOf[InvalidAccelResponse]
      ) :: commandExpectsResponse
    }

    val beethovenCustomCommandManager = Module(
      new BeethovenCommandBundler[T1, T2](
        bundleIn,
        bundleOut,
        outer,
        !bundleOut.isInstanceOf[InvalidAccelResponse],
        opCode
      )
    )
    beethovenCustomCommandManager.cio.req.bits := DontCare
    beethovenCustomCommandManager.cio.req.valid := false.B
    beethovenCustomCommandManager.cio.resp.ready := false.B
    if (!bundleOut.isInstanceOf[InvalidAccelResponse]) {
      beethovenCustomCommandManager.io.resp.valid := false.B
      beethovenCustomCommandManager.io.resp.bits.rd := DontCare
    }

    beethovenCustomCommandManager.suggestName(
      outer.systemParams.name + "CustomCommand"
    )

    when(io_declaration.req.bits.inst.funct === opCode.U) {
      beethovenCustomCommandManager.cio.req <> io_declaration.req
    }

    io_declaration.resp.valid := beethovenCustomCommandManager.cio.resp.valid
    io_declaration.resp.bits := beethovenCustomCommandManager.cio.resp.bits
    beethovenCustomCommandManager.cio.resp.ready := io_declaration.resp.ready


    nCommands = nCommands + 1
    beethovenCustomCommandManager.io
  }

  def RoccBeethovenIO(): RoccExchange = {
    if (using_custom == CustomCommandUsage.custom) {
      throw new Exception("Cannot use io after generating a custom io")
    }
    using_custom = CustomCommandUsage.default
    io_declaration
  }

  val beethoven_rocc_exchanges = outer.systemParams.canIssueCoreCommandsTo.map {
    target =>
      (
        target, {
          val io = IO(new RoccExchange())
          io.suggestName(f"externalCommandInterface_to${target}")
          io
        }
      )
  }

  class IntraCoreIO[Tcmd <: AccelCommand, Tresp <: AccelResponse](
      genCmd: Tcmd,
      genResp: Tresp
  ) extends Bundle {
    val req = Decoupled(new Bundle {
      val payload: Tcmd = genCmd.cloneType
      val target_core_idx: UInt = UInt(CoreIDLengthKey.W)
    })
    val resp = Flipped(Decoupled(genResp.cloneType))
  }

  def getIntraSysIO[T <: AccelCommand, R <: AccelResponse](
      targetSys: String,
      cmdName: String,
      cmdGen: T,
      respGen: R
  ): IntraCoreIO[T, R] = {
    val target_exchange = beethoven_rocc_exchanges.find(_._1 == targetSys).get
    val sys_io = Wire(Output(new IntraCoreIO[T, R](cmdGen, respGen)))
    val opcode =
      systemOpCodeMap.filter(a => a._1 == targetSys && a._2 == cmdName) match {
        case Nil =>
          throw new Exception(
            s"Could not find opcode for $cmdName in $targetSys"
          )
        case _ =>
          systemOpCodeMap
            .filter(a => a._1 == targetSys && a._2 == cmdName)
            .head
            ._3
      }
    val expectResponse = commandExpectsResponse.filter(a =>
      a._1 == targetSys && a._2 == cmdName
    ) match {
      case Nil =>
        throw new Exception(
          s"Could not find response expectation for $cmdName in $targetSys"
        )
      case _ =>
        commandExpectsResponse
          .filter(a => a._1 == targetSys && a._2 == cmdName)
          .head
          ._3
    }
    val emitter = Module(
      new MultiBeatCommandEmitter[T](cmdGen, expectResponse, opcode)
    )
    emitter.out <> target_exchange._2.req
    sys_io.req.ready := emitter.in.ready
    emitter.in.valid := sys_io.req.valid
    emitter.in.bits <> sys_io.req.bits.payload
    emitter.in.bits.__core_id := sys_io.req.bits.target_core_idx
    emitter.in.bits.__system_id := getSystemID(targetSys)
    sys_io.resp.valid := target_exchange._2.resp.valid
    sys_io.resp.bits.getDataField := target_exchange._2.resp.bits.getDataField
    sys_io.resp.bits.rd := target_exchange._2.resp.bits.rd
    sys_io.resp.bits.elements("core_id") := target_exchange._2.resp.bits.core_id
    sys_io.resp.bits.elements(
      "system_id"
    ) := target_exchange._2.resp.bits.system_id

    target_exchange._2.resp.ready := sys_io.resp.ready

    sys_io
  }
}
