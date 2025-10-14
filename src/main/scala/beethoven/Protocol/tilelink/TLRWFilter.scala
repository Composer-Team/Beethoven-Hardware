package beethoven.Protocol.tilelink

import beethoven.Protocol.tilelink.TLSlave
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.diplomacy._
import org.chipsalliance.diplomacy.tilelink._
import chisel3._
import chisel3.util.Queue

class TLRWFilter(spp: TLSlavePortParameters, mpp: TLMasterPortParameters)(
    implicit p: Parameters
) extends LazyModule {

  val in_node = TLManagerNode(Seq(spp))
  val read_out = TLClientNode(
    Seq(
      TLMasterPortParameters.v1(
        Seq(
          mpp
            .masters(0)
            .v1copy(
              supportsPutFull = TransferSizes.none,
              supportsPutPartial = TransferSizes.none
            )
        )
      )
    )
  )

  val write_out = TLClientNode(
    Seq(
      TLMasterPortParameters.v1(
        Seq(mpp.masters(0).v1copy(supportsGet = TransferSizes.none))
      )
    )
  )

  lazy val module = new TLRWFilterImp(this)
}
