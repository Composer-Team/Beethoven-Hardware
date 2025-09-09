package beethoven.Protocol.FrontBus

import beethoven.BuildMode
import beethoven.Platforms.BuildModeKey
import beethoven.Protocol.AXI.{AXI4Compat, ClockCrossing}
import chipsalliance.rocketchip.config.{Field, Parameters}
import freechips.rocketchip.diplomacy.LazyModule

case object FastMemClockCross extends Field[Seq[ClockCrossing]]

class AXIFrontBusProtocolFastMem(withDMA: Boolean)
    extends AXIFrontBusProtocol(withDMA = withDMA, nClocks = 2) {
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
      val resets = p_conf(ResetKey)
      val crosses = p_conf(FastMemClockCross)
      crosses.foreach { cross =>
        cross.module.io.clock_src := clocks(0)
        cross.module.io.reset_src := !resets(0)
        cross.module.io.clock_dst := clocks(1)
        cross.module.io.reset_dst := !resets(1)
      }
    }
    p_conf
  }

}
