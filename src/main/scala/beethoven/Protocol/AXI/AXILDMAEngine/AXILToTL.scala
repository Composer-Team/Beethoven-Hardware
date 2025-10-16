package beethoven.Protocol.AXI.AXILDMAEngine

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy.amba.axi4.AXI4ToTLNode
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.amba.axi4._
import org.chipsalliance.diplomacy.tilelink._
import org.chipsalliance.diplomacy.nodes._
import beethoven.common.CLog2Up
case class AXILToDifferentWidthTLNode(val axiWidthBytes: Int, tlSources: Int = 4)(implicit valName: ValName)
    extends MixedAdapterNode(AXI4Imp, TLImp)(
      dFn = { case mp =>
        TLMasterPortParameters.v1(clients = Seq(TLMasterParameters.v1(name = "AXILToDWTL", sourceId = IdRange(0, tlSources))))
      },
      uFn = { mp =>
        AXI4SlavePortParameters(
          slaves = mp.managers.map { m =>
            AXI4SlaveParameters(
              address = m.address,
              supportsWrite = TransferSizes(axiWidthBytes),
              supportsRead = TransferSizes(axiWidthBytes),
              interleavedId = Some(0)
            )
          }, // TL2 never interleaves D beats
          beatBytes = axiWidthBytes
        )
      }
    )

class AXILToTL(wAXI: Int)(implicit
    p: Parameters
) extends LazyModule {
  val node = AXILToDifferentWidthTLNode(wAXI)
  lazy val module = new LazyModuleImp(this) {
    val axi = node.in(0)._1
    val tl = node.out(0)._1
    val wTL = tl.d.bits.data.getWidth / 8
    val logWTL = CLog2Up(wTL)
    val logWAXI = CLog2Up(wAXI)
    val addrW = tl.a.bits.address.getWidth
    val idle :: active_read :: active_write :: Nil = Enum(3)
    val state = RegInit(idle)
    // prefer read over write because we need to buffer writes anyway
    val read_supercedes_write = axi.ar.valid
    tl.d.ready := false.B

    val max_write_source = node.out(0)._2.master.endSourceId
    val source_width = log2Up(max_write_source)
    require(isPow2(max_write_source))
    val sourceTable = Module(new Queue(UInt(tl.d.bits.source.getWidth.W), max_write_source))
    sourceTable.io.enq.valid := false.B
    sourceTable.io.enq.bits := DontCare
    sourceTable.io.deq.ready := false.B

    // initialize the source table
    locally {
      val m_loop :: m_idle :: Nil = Enum(2)
      val m_state = RegInit(m_loop)
      val m_count = RegInit(1.U(log2Up(max_write_source).W))

      sourceTable.io.enq.valid := m_state === m_loop ||
        (tl.d.fire && (tl.d.bits.opcode === TLMessages.PutFullData || tl.d.bits.opcode === TLMessages.PutPartialData))
      sourceTable.io.enq.bits := Mux(m_state === m_loop, m_count, tl.d.bits.source)
      when(m_state === m_loop) {
        when(sourceTable.io.enq.fire) {
          m_count := m_count + 1.U
          when(m_count === (max_write_source - 1).U) {
            m_state := m_idle
          }
        }
      }
    }

    val n_credits = 64
    val credit_width = log2Up(n_credits)

    val writer = Module(
      new AXIL_W_TO_TL(wAXI, node.out(0)._1.params, node.out(0)._2, source_width, credit_width)
    )
    val reader = Module(new AXIL_R_TO_TL(wAXI, node.out(0)._1.params, node.out(0)._2, credit_width))

    writer.source_io <> sourceTable.io.deq
    reader.available_credits := writer.credits

    // local block provides a scope to collapse in your editor...
    locally {
      reader.axi.ar.valid := axi.ar.valid
      axi.ar.ready := reader.axi.ar.ready
      reader.axi.ar.bits.addr := axi.ar.bits.addr

      axi.r.valid := reader.axi.r.valid
      reader.axi.r.ready := axi.r.ready
      axi.r.bits.data := reader.axi.r.bits.data
      axi.r.bits.resp := reader.axi.r.bits.resp

      writer.axi.aw.valid := axi.aw.valid
      writer.axi.aw.bits.addr := axi.aw.bits.addr
      axi.aw.ready := writer.axi.aw.ready

      writer.axi.w.valid := axi.w.valid
      writer.axi.w.bits.data := axi.w.bits.data
      writer.axi.w.bits.strb := axi.w.bits.strb
      axi.w.ready := writer.axi.w.ready

      axi.b.valid := writer.axi.b.valid
      writer.axi.b.ready := axi.b.ready
      axi.b.bits.resp := writer.axi.b.bits.resp
    }

    val arbiter = Module(new Arbiter(tl.a.bits.cloneType, 2))
    tl.a <> arbiter.io.out
    arbiter.io.in(0) <> reader.tl.a
    arbiter.io.in(1) <> writer.tl.a

    val response_is_read = tl.d.bits.source === 0.U
    writer.tl.d.bits := tl.d.bits
    writer.tl.d.valid := tl.d.valid && !response_is_read
    reader.tl.d.bits := tl.d.bits
    reader.tl.d.valid := tl.d.valid && response_is_read
    tl.d.ready := Mux(response_is_read, reader.tl.d.ready, writer.tl.d.ready)

  }
}
