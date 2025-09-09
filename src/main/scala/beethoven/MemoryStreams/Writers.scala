package beethoven.MemoryStreams

import beethoven.ChannelTransactionBundle
import chisel3._
import chisel3.util._
import chipsalliance.rocketchip.config._
import beethoven.{BeethovenBuild, platform}
import beethoven.common.{CLog2Up, Misc, Stack, splitIntoChunks}
import freechips.rocketchip.tilelink._

class WriterDataChannelIO(val dWidth: Int) extends Bundle {
  val data = Flipped(Decoupled(UInt(dWidth.W)))
  val isFlushed = Output(Bool())
}

class SequentialWriteChannelIO(maxBytes: Int)(implicit p: Parameters)
    extends Bundle {
  val req = Flipped(Decoupled(new ChannelTransactionBundle))
  val channel = new WriterDataChannelIO(maxBytes * 8)
  val busy = Output(Bool())
}

/** Writes out a set number of fixed-size items sequentially to memory.
  *
  * @param userBytes
  *   the number of bytes in a single item
  */
class SequentialWriter(
    userBytes: Int,
    val tl_outer: TLBundle,
    edge: TLEdgeOut,
    minSizeBytes: Option[Int] = None
)(implicit p: Parameters)
    extends Module {
  override val desiredName = s"SequentialWriter_w${userBytes * 8}"
  require(
    isPow2(userBytes),
    "Writer must have a data channel with that is a power-of-2 number of bytes wide."
  )
  private val fabricBeatBytes = tl_outer.params.dataBits / 8
  private val addressBits = tl_outer.params.addressBits
  private val addressBitsChop = addressBits - log2Up(fabricBeatBytes)
  private val nSources = edge.master.endSourceId
  val pfsm = platform.prefetchSourceMultiplicity
  val userBeatsPerLargeTx = fabricBeatBytes * pfsm / userBytes
  require(
    isPow2(pfsm) && pfsm > 1,
    "Platform Developer: This platform has seemed to declare an invalid number of" +
      "sources per Writer. It must be a power-of-two and greater than 1."
  )

  val io = IO(new SequentialWriteChannelIO(userBytes))
  val tl_out = IO(new TLBundle(tl_outer.params))
  val idle = RegInit(true.B)
  io.busy := !idle
  io.req.ready := idle
  io.channel.data.ready := false.B
  tl_out.a.valid := false.B
  tl_out.a.bits := DontCare

  val q_size =
    Math.max(minSizeBytes.getOrElse(0) / fabricBeatBytes, pfsm * nSources)

  val burst_storage_io = Module(
    new Queue(
      UInt(tl_outer.params.dataBits.W),
      platform.prefetchSourceMultiplicity,
      pipe = true,
      useSyncReadMem =
        true // hopefully this gives us BRAM in FPGA. Worry about ASIC later ugh
    )
  ).io

  // logical operation "a -> b"
  def implies(a: Bool, b: Bool): Bool = (a && b) || !a

  val localMaskBits = tl_outer.params.dataBits / 8
  val memory_latency = 3
  val write_buffer_payload = Wire(UInt(tl_outer.params.dataBits.W))
  write_buffer_payload := DontCare

  val write_buffer =
    Memory(memory_latency, tl_outer.params.dataBits, q_size, 1, 1, 0)
  val write_buffer_occupancy = RegInit(0.U(log2Up(q_size + 1).W))
  val write_buffer_read_shift = RegInit(0.U(memory_latency.W))
  val burst_storage_occupancy = RegInit(
    0.U(log2Up(platform.prefetchSourceMultiplicity + 1).W)
  )
  val raddr = RegInit(0.U(log2Up(q_size).W))
  val waddr = RegInit(0.U(log2Up(q_size).W))
  val (wb_widx, wb_ridx) =
    if (write_buffer.nWritePorts == 0) (0, 1)
    else (write_buffer.getWritePortIdx(0), write_buffer.getReadPortIdx(0))

  write_buffer_read_shift := Cat(
    write_buffer.chip_select(wb_ridx),
    write_buffer_read_shift >> 1
  )

  val enable_buffer_write = WireInit(false.B)
  write_buffer.initLow(clock)
  write_buffer.addr(wb_widx) := waddr
  write_buffer.write_enable(wb_widx) := true.B
  write_buffer.data_in(wb_widx) := write_buffer_payload
  write_buffer.chip_select(wb_widx) := enable_buffer_write
  waddr := waddr + write_buffer.chip_select(wb_widx)

  val mask_buffer = if (userBytes < fabricBeatBytes) {
    val q = Module(
      new Queue[UInt](
        UInt((fabricBeatBytes / userBytes).W),
        q_size + platform.prefetchSourceMultiplicity + 1
      )
    )
//    println(s"mask buffer is sz ${q.entries}")
    q.io.enq.valid := enable_buffer_write
    q.io.enq.bits := 0.U
    q.io.deq.ready := tl_out.a.fire
    when(q.io.enq.valid) {
      assert(q.io.enq.ready, "Mask buffer needs to be made bigger")
    }
    Some(q)
  } else None

  val wbuff_re =
    burst_storage_occupancy < platform.prefetchSourceMultiplicity.U && write_buffer_occupancy > 0.U
  val wbuff_we = enable_buffer_write

  write_buffer.addr(wb_ridx) := raddr
  write_buffer.read_enable(wb_ridx) := true.B
  write_buffer.chip_select(wb_ridx) := wbuff_re
  burst_storage_io.enq.valid := write_buffer_read_shift(0)
  burst_storage_io.enq.bits := write_buffer.data_out(wb_ridx)
  assert(implies(burst_storage_io.enq.valid, burst_storage_io.enq.ready))
  raddr := raddr + write_buffer.chip_select(wb_ridx)

  val wb_occ_mo = write_buffer_occupancy - 1.U
  val wb_occ_po = write_buffer_occupancy + 1.U

  write_buffer_occupancy := Mux1H(
    Seq(
      // don't change
      (wbuff_re && wbuff_we) || (!wbuff_re && !wbuff_we),
      // add to buffer
      !wbuff_re && wbuff_we,
      // sub from buffer
      wbuff_re && !wbuff_we
    ),
    Seq(write_buffer_occupancy, wb_occ_po, wb_occ_mo)
  )

  val buff_deq = burst_storage_io.deq.fire
  val buff_enq = wbuff_re

  burst_storage_occupancy := Mux1H(
    Seq(
      (buff_deq && buff_enq) || (!buff_deq && !buff_enq),
      buff_deq && !buff_enq,
      !buff_deq && buff_enq
    ),
    Seq(
      burst_storage_occupancy,
      burst_storage_occupancy - 1.U,
      burst_storage_occupancy + 1.U
    )
  )

  burst_storage_io.deq.ready := tl_out.a.fire

  // keep two different counts so that we can keep enqueueing while bursting
  val burst_progress_count = RegInit(0.U(log2Up(pfsm).W))

  val req_addr = RegInit(0.U(addressBitsChop.W))

  val sourceBusyBits = RegInit(
    VecInit(Seq.fill(edge.master.endSourceId)(false.B))
  )
//  val sourcesInProgress = sourceBusyBits.fold(false.B)(_ || _)
  val hasAvailableSource =
    (~sourceBusyBits.asUInt).asBools.fold(false.B)(_ || _)
  val nextSource = PriorityEncoder(~sourceBusyBits.asUInt)

  val burst_inProgress = RegInit(false.B)
  val sourceInProgress = Reg(UInt(tl_out.params.sourceBits.W))
  val addrInProgress = Reg(UInt(addressBits.W))

  io.channel.isFlushed := sourceBusyBits.asUInt === 0.U

  val expectedNumBeats = RegInit(0.U((addressBits - log2Up(userBytes)).W))
  when(idle) {
    when(io.req.fire) {
      idle := false.B
      val choppedAddr = (io.req.bits.addr >> log2Up(fabricBeatBytes)).asUInt
      expectedNumBeats := io.req.bits.len >> CLog2Up(userBytes)
      if (userBytes > 1) {
        assert(
          io.req.bits.len(CLog2Up(userBytes) - 1, 0) === 0.U,
          "Writer: can't write less than channel width "
        )
      }
      req_addr := choppedAddr
      burst_progress_count := 0.U
    }
  }.otherwise {
    when(
      expectedNumBeats === 0.U && write_buffer_occupancy === 0.U && burst_storage_occupancy === 0.U
    ) {
      idle := true.B
    }
  }
  // these are always true
  tl_out.a.bits.mask := (if (mask_buffer.isDefined) {
                           val q = mask_buffer.get
                           when(q.io.deq.ready) {
                             assert(q.io.deq.valid)
                           }
                           Misc.maskDemux(q.io.deq.bits, userBytes)
                         } else BigInt("1" * fabricBeatBytes, radix = 2).U)
  tl_out.a.bits.opcode := TLMessages.PutFullData

  when(burst_inProgress) {
    tl_out.a.valid := true.B
    assert(burst_storage_io.deq.valid)
    tl_out.a.bits.address := addrInProgress
    tl_out.a.bits.size := log2Up(
      platform.prefetchSourceMultiplicity * fabricBeatBytes
    ).U
    tl_out.a.bits.data := burst_storage_io.deq.bits
    when(tl_out.a.fire) {
      burst_progress_count := burst_progress_count + 1.U
      when(burst_progress_count === (pfsm - 1).U) {
        // try to allocate
        burst_inProgress := false.B
      }
    }
  }.otherwise {
    val isSmall = expectedNumBeats < userBeatsPerLargeTx.U
    val burstSize = Mux(isSmall, 1.U, pfsm.U)
    tl_out.a.valid := hasAvailableSource && burst_storage_occupancy >= burstSize && burst_storage_io.deq.valid
    require(
      platform.prefetchSourceMultiplicity >= memory_latency,
      """
        |If the valid signal is high, there is _at least_ one thing in the burst queue. For burst storage
        |occupancy = bso and memory latency = ml and bso >= ml, we know that bso can be greater than the real
        |occupancy of the queue. However, if bso >= burst length, then within ml cycles, there will have been
        |at least bso elements within the queue, guaranteed by bso >= ml.
        |""".stripMargin
    )
    val nextAddr = Cat(req_addr, 0.U(log2Up(fabricBeatBytes).W))

    tl_out.a.bits.data := burst_storage_io.deq.bits
    tl_out.a.bits.size := Mux(
      isSmall,
      log2Up(fabricBeatBytes).U,
      log2Up(fabricBeatBytes * pfsm).U
    )
    tl_out.a.bits.address := nextAddr
    tl_out.a.bits.source := nextSource
    when(tl_out.a.fire) {
      sourceBusyBits(nextSource) := true.B
      sourceInProgress := nextSource
      addrInProgress := nextAddr
      req_addr := req_addr + burstSize
      when(!isSmall) {
        burst_inProgress := true.B
        burst_progress_count := 1.U
      }
    }
  }

  if (userBytes == fabricBeatBytes) {
    write_buffer_payload := io.channel.data.bits
    enable_buffer_write := io.channel.data.fire && write_buffer_occupancy < q_size.U
    io.channel.data.ready := write_buffer_occupancy < q_size.U && expectedNumBeats > 0.U
    when(io.channel.data.fire) {
      expectedNumBeats := expectedNumBeats - 1.U
    }
  } else if (userBytes < fabricBeatBytes) {
    val beatLim = fabricBeatBytes / userBytes
    val beatBuffer = Reg(Vec(beatLim - 1, UInt((userBytes * 8).W)))
    val beatCounter = Reg(UInt(log2Up(beatLim).W))
    io.channel.data.ready := write_buffer_occupancy < q_size.U && expectedNumBeats > 0.U
    val maskAcc = RegInit(VecInit(Seq.fill(beatLim - 1)(false.B)))
    when(io.req.fire) {
      val upper = log2Up(fabricBeatBytes) - 1
      val lower = CLog2Up(userBytes)

      /** What if your channel wants to do an unaligned access? For instance,
        * with a 32b bus, you're trying to do a 16b write to the address 0x2.
        * It's illegal to emit a transaction for 0x2, so you have to start it at
        * 0x0, mask out the two bottom bytes, and THEN put the bytes of interest
        * on the higher-order bits. If we run into this situation, then we'll
        * just initialize beatCounter higher. It'll be filled with
        * old/uninitialized data but who cares?
        */
      beatCounter := io.req.bits.addr(upper, lower)
//      printf("Address: %x\tStart: %d\n", io.req.bits.addr, io.req.bits.addr(upper, lower))
    }
    when(io.channel.data.fire) {
      expectedNumBeats := expectedNumBeats - 1.U
      val bytesGrouped = (0 until userBytes).map(i =>
        io.channel.data.bits((i + 1) * 8 - 1, i * 8)
      )
      beatBuffer(beatCounter) := Cat(bytesGrouped.reverse)
      beatCounter := beatCounter + 1.U
      maskAcc(beatCounter) := true.B
      when(beatCounter === (beatLim - 1).U || expectedNumBeats === 1.U) {
        enable_buffer_write := true.B
        val bgc = Cat(bytesGrouped.reverse)
        val beatBuffer_concat = Wire(Vec(beatLim, UInt((userBytes * 8).W)))
        val maskAcc_concat = Wire(Vec(beatLim, Bool()))
        (0 to beatLim - 2) foreach { t =>
          beatBuffer_concat(t) := beatBuffer(t)
          maskAcc_concat(t) := maskAcc(t)
        }
        beatBuffer_concat.last := DontCare
        maskAcc_concat.last := false.B
        beatBuffer_concat(beatCounter) := bgc
        maskAcc_concat(beatCounter) := true.B
        write_buffer_payload := Cat(beatBuffer_concat.reverse)
        mask_buffer.get.io.enq.bits := Cat(maskAcc_concat.reverse)
        maskAcc.foreach(_ := false.B)
        beatCounter := 0.U
      }
    }
  } else {
    val dsplit = splitIntoChunks(io.channel.data.bits, fabricBeatBytes * 8)
    val inProgressPushing = RegInit(false.B)
    val channelReg = Reg(
      Vec(userBytes / fabricBeatBytes, UInt((fabricBeatBytes * 8).W))
    ) // bottom chunk will get optimized away
    val dsplitCount = Reg(UInt(log2Up(userBytes / fabricBeatBytes + 1).W))
    when(!inProgressPushing) {
      io.channel.data.ready := expectedNumBeats > 0.U && write_buffer_occupancy < q_size.U
      write_buffer_payload := dsplit(0)
      enable_buffer_write := expectedNumBeats > 0.U && io.channel.data.valid
      when(io.channel.data.fire) {
        inProgressPushing := true.B
        channelReg := dsplit
        dsplitCount := 1.U
      }
    }.otherwise {
      io.channel.data.ready := false.B
      enable_buffer_write := write_buffer_occupancy < q_size.U
      write_buffer_payload := channelReg(dsplitCount)
      when(enable_buffer_write) {
        dsplitCount := dsplitCount + 1.U
        when(dsplitCount === (userBytes / fabricBeatBytes - 1).U) {
          inProgressPushing := false.B
        }
      }
    }
  }

  tl_out.d.ready := true.B
  when(tl_out.d.fire) {
    sourceBusyBits(tl_out.d.bits.source) := false.B
  }
}
