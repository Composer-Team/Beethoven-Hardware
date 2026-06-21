package beethoven.MemoryStreams

import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.tilelink.TLManagerNode
import org.chipsalliance.diplomacy.tilelink.TLClientNode
import org.chipsalliance.diplomacy.tilelink.TLMasterPortParameters
import org.chipsalliance.diplomacy.tilelink.TLMasterParameters
import beethoven.platform
import org.chipsalliance.cde.config.Parameters
import chisel3.fromBooleanToLiteral
import firtoolresolver.shaded.coursier.core.Done
import chisel3.DontCare

class LazyTLTieOff(read: Boolean)(implicit p: Parameters) extends LazyModule {
    val node = TLClientNode(
        portParams = Seq(TLMasterPortParameters.v1(
            clients = Seq(TLMasterParameters.v1(
                "tie-off",
                supportsGet = if (read) TransferSizes(platform.memoryControllerBeatBytes) else TransferSizes.none,
                supportsPutFull = if (!read) TransferSizes(platform.memoryControllerBeatBytes) else TransferSizes.none,
                supportsProbe = if (!read) TransferSizes(platform.memoryControllerBeatBytes) else TransferSizes.none
            ))
        ))
    )

    def module: LazyModuleImpLike = new LazyModuleImp(this) {
        val tlb = node.out(0)._1
        tlb.a.valid := false.B
        tlb.a.bits := DontCare

        tlb.d.ready := false.B
    }
}

object LazyTLTieOff {
    def apply(read: Boolean)(implicit p: Parameters): TLClientNode = {
        return LazyModule(new LazyTLTieOff(read)).node
    }
}