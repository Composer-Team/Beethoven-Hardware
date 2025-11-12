// See LICENSE.SiFive for license details.

package beethoven.Protocol.tilelink
import chisel3._
import chisel3.util._
import beethoven.Protocol.tilelink.TLSlave
import org.chipsalliance.cde.config._
import beethoven.platform
import chisel3.VecInit
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.tilelink._
import org.chipsalliance.diplomacy.util._
import dataclass.data

class TLSourceShrinkerDynamicBlocking(maxNIDs: Int)(implicit p: Parameters) extends LazyModule {
  require(maxNIDs > 0)

  private def noShrinkRequired(client: TLClientPortParameters): Boolean =
    maxNIDs >= client.endSourceId

  // The SourceShrinker completely destroys all FIFO property guarantees
  private val client = TLMasterParameters.v1(
    name = "TLSourceShrinker2",
    sourceId = IdRange(0, maxNIDs)
  )
  val node = new TLAdapterNode(
    clientFn = { cp =>
      if (noShrinkRequired(cp)) {
//        println(s"no shrink: ${cp.allSupportPutFull} ${cp.allSupportGet}")
        cp
      } else {
//        println(s"shrink pre: ${cp.allSupportPutFull} ${cp.allSupportGet}")
        // We erase all client information since we crush the source Ids
        val q = TLMasterPortParameters.v1(
          clients = Seq(
            client.v1copy(
              requestFifo = cp.clients.exists(_.requestFifo),
              supportsGet = cp.allSupportGet,
              supportsPutFull = cp.allSupportPutFull,
              supportsProbe = cp.allSupportProbe
            )
          ),
          echoFields = cp.echoFields,
          requestFields = cp.requestFields,
          responseKeys = cp.responseKeys
        )
//        println(s"shrink post: ${q.allSupportPutFull} ${q.allSupportGet}")
        q
      }
    },
    managerFn = { mp =>
      mp.v1copy(managers =
        mp.managers.map(m => m.v1copy(fifoId = if (maxNIDs == 1) Some(0) else m.fifoId))
      )
    }
  ) {
    override def circuitIdentity =
      edges.in.map(_.client).forall(noShrinkRequired)
  }

  lazy val module = new Impl

  class Impl extends LazyModuleImp(this) {
    node.in.zip(node.out).foreach { case ((in, edgeIn), (out, edgeOut)) =>
      // Acquires cannot pass this adapter; it makes Probes impossible
      require(
        !edgeIn.client.anySupportProbe || !edgeOut.manager.anySupportAcquireB
      )

      out.b.ready := true.B
      out.c.valid := false.B
      out.e.valid := false.B
      in.b.valid := false.B
      in.c.ready := true.B
      in.e.ready := true.B

      if (noShrinkRequired(edgeIn.client)) {
        out.a <> in.a
        in.d <> out.d
      } else {
        val sourceOut2InMap =
          Reg(Vec(maxNIDs, UInt(width = log2Up(edgeIn.client.endSourceId).W)))

        val allocated = RegInit(VecInit(Seq.fill(maxNIDs)(false.B)))
        val max_n_beats = edgeOut.manager.maxTransfer / edgeOut.manager.beatBytes
        val beatCountWidth = log2Up(max_n_beats)
        val beatsLeftPerAllocation =
          RegInit(Vec(maxNIDs, UInt(beatCountWidth.W)), VecInit(Seq.fill(maxNIDs)(0.U)))
        val nextFree = PriorityEncoder(~allocated)
        val full = allocated.andR
        val a_in_valid = RegInit(false.B)
        val a_in = Reg(in.a.bits.cloneType)
        out.a.valid := a_in_valid
        out.a.bits := a_in

        // need to count beats in the transaction because we might follow two transactions back to back with each other

        val handlingLongWriteTx = RegInit(Bool(), false.B)
        val prevSourceMap = Reg(UInt(out.params.sourceBits.W))
        val prevSource = Reg(UInt(in.params.sourceBits.W))
        val singleBeatLgSz = log2Up(in.a.bits.data.getWidth / 8)

        in.a.ready := handlingLongWriteTx || (
          !full && (out.a.fire || !a_in_valid)
        )
        val longBeatExp, longBeatCount = RegInit(
          UInt(log2Up(Math.max(63, platform.prefetchSourceMultiplicity - 1)).W),
          0.U
        )

        // this HAS to be the case, otherwise we risk deadlock. But I think it's also just part of the
        // TileLink standard that requests must be provided on contiguous beats.
        // see page 20 of TileLink standard v1.9.3
        assert(!handlingLongWriteTx || !in.a.valid || prevSource === in.a.bits.source)

        when(out.a.fire && !in.a.fire) {
          a_in_valid := false.B
        }

        when(in.a.fire) {
          a_in := in.a.bits
          a_in_valid := true.B
          prevSource := in.a.bits.source
          when(handlingLongWriteTx) {
            a_in.source := prevSourceMap
            longBeatCount := longBeatCount + 1.U
            when(longBeatCount === longBeatExp) {
              handlingLongWriteTx := false.B
            }
          }.otherwise {
            val data_width = in.a.bits.data.getWidth / 8
            val lognbeats = in.a.bits.size - log2Up(data_width).U
            // shift out to give us # of beats
            val nbeats = 1.U << lognbeats
            when(
              in.a.bits.opcode === TLMessages.PutFullData && in.a.bits.size > singleBeatLgSz.U
            ) {
              handlingLongWriteTx := true.B
              // size gives us log2 #ofbytes
              // subtract by databus width to give us log2 #ofbeats

              longBeatExp := nbeats
              longBeatCount := 1.U
            }.otherwise {
              handlingLongWriteTx := false.B
            }
            allocated(nextFree) := true.B
            sourceOut2InMap(nextFree) := in.a.bits.source
            a_in.source := nextFree
            beatsLeftPerAllocation(nextFree) :=
              Mux(
                in.a.bits.opcode === TLMessages.PutFullData,
                0.U, // if write then we only expect 1 write response
                nbeats - 1.U
              ) // if read, then many responses
            assert(
              in.a.bits.size >= log2Up(edgeOut.manager.beatBytes).U,
              "TLSourceShrinker2: Request too small"
            )
          }
        }

        val d_in = Reg(in.d.bits.cloneType)
        val d_in_valid = RegInit(false.B)
        in.d.bits := d_in
        in.d.valid := d_in_valid
        out.d.ready := (d_in_valid && in.d.ready) || !d_in_valid
        when(in.d.fire) {
          d_in_valid := false.B
        }
        when(out.d.fire) {
          val beatsLeft = beatsLeftPerAllocation(out.d.bits.source)
          d_in := out.d.bits
          d_in_valid := true.B
          d_in.source := sourceOut2InMap(out.d.bits.source)
          beatsLeftPerAllocation(out.d.bits.source) := beatsLeft - 1.U
          when(beatsLeft === 0.U) {
            allocated(out.d.bits.source) := false.B
          }
        }
      }
    }
  }
}

object TLSourceShrinkerDynamicBlocking {
  def apply(maxInFlight: Int, suggestedName: Option[String] = None)(implicit
      p: Parameters
  ): TLNode = {
    val shrinker = LazyModule(new TLSourceShrinkerDynamicBlocking(maxInFlight))
    shrinker.suggestName(suggestedName)
    shrinker.node
  }
}
