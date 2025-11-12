package beethoven.Protocol.FrontBus

import beethoven.BuildMode
import beethoven.Platforms.BuildModeKey
import beethoven.Protocol.AXI.{AXI4Compat, ClockCrossing}
import org.chipsalliance.cde.config.{Field, Parameters}
import org.chipsalliance.diplomacy.LazyModule

case object FastMemClockCross extends Field[Seq[ClockCrossing]]

class AXIFrontBusProtocolFastMem extends AXIFrontBusProtocol(nClocks = 2) {
  override def deriveTLSources(implicit p: Parameters): Parameters = {
    val p_conf = super.deriveTLSources(p)
    if (p(BuildModeKey) == BuildMode.Synthesis) {
      val crosses = p_conf(BeethovenInternalMemKey).map { fr =>
        val cross = LazyModule(new ClockCrossing())
        fr := cross.node
        cross
      }

      val mem_internals = crosses.map(_.node)

      p_conf.alterPartial({
        case BeethovenInternalMemKey => mem_internals
        case FastMemClockCross       => crosses
      })
    } else p_conf
  }

  override def deriveTopIOs(
      config: Parameters
  )(implicit p: Parameters): Parameters = {
    val p_conf = super.deriveTopIOs(config)
    if (p(BuildModeKey) == BuildMode.Synthesis) {
      val clocks = p_conf(ClockKey)
      val aresets = p_conf(ResetKey)
      val crosses = p_conf(FastMemClockCross)
      crosses.foreach { cross =>
        cross.module.io.clock_src := clocks(0)
        cross.module.io.areset_src := (!aresets(0).asBool).asAsyncReset
        cross.module.io.clock_dst := clocks(1)
        cross.module.io.areset_dst := (!aresets(1).asBool).asAsyncReset
      }
    }
    p_conf
  }

}
