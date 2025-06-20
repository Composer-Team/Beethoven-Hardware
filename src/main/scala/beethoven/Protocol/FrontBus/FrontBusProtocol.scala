package beethoven.Protocol.FrontBus

import beethoven.Protocol.RoCC.{RoccClientNode, RoccIdentityNode, RoccNode}
import chipsalliance.rocketchip.config.{Config, Field, Parameters}
import chisel3._
import freechips.rocketchip.amba.axi4.{AXI4Node, AXI4SlaveNode}
import freechips.rocketchip.tilelink.{TLIdentityNode, TLNode}


abstract class FrontBusProtocol {
  /**
   * tlChainObj provides the diplomacy objects generated in `deriveTLSources` so that you, from a non-lazy context
   * can generate the physical IOs and tie them to the diplomacy object bundle IOs
   */
  def deriveTopIOs(config: Parameters)(implicit p: Parameters): Parameters

  /**
   * This function is executed from the lazy context. Generate the following:
   * 1. the top-level protocol nodes of your choice. These will be `Any`-typed and passed to `deriveTopIOs` to be
   * tied-off.
   * 2. A `TLIdentityNode` with a driver. In the simple case, it is directly driven by the aforementioned protocol
   * nodes. The generated node (2) is used to drive the front-bus modules and receive commands to the accelerator
   * system.
   * 3. Optionally, there may be DMA from the front-bus-associated modules, so those can be exposed here as well
   * in the TileLink format.
   */
  def deriveTLSources(implicit p: Parameters): Parameters
}

// MUST DEFINE THE FOLLOWING
case object OptionalPassKey extends Field[Any]

case object RoccNodeKey extends Field[RoccNode]

case object DMANodeKey extends Field[Option[TLIdentityNode]]

case object ClockKey extends Field[Seq[Clock]]

case object ResetKey extends Field[Seq[Bool]]

case object BeethovenInternalMemKey extends Field[Seq[AXI4Node]]

// CAN OPTIONALLY DEFINE THIS ONE
case object DebugCacheProtSignalKey extends Field[Option[UInt]]