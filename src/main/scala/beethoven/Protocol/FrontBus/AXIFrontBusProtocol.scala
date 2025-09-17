package beethoven.Protocol.FrontBus

import beethoven.Floorplanning.DeviceContext
import beethoven.Floorplanning.LazyModuleWithSLRs.LazyModuleWithFloorplan
import beethoven.Generation.DotGen
import chipsalliance.rocketchip.config.{Config, Parameters}
import chisel3._
import beethoven.Platforms._
import beethoven._
import beethoven.Protocol.AXI.{AXI4Compat, LongAXI4ToTL}
import beethoven.Protocol.RoCC.Helpers.FrontBusHub
import beethoven.Protocol.RoCC._
import beethoven.Systems.BeethovenTop.getAddressSet
import beethoven.Systems.make_tl_buffer
import beethoven.platform
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.MasterPortParams
import freechips.rocketchip.tilelink.{TLBuffer, TLIdentityNode}

class AXIFrontBusProtocol(withDMA: Boolean, nClocks: Int = 1)
    extends FrontBusProtocol {
  override def deriveTopIOs(
      config: Parameters
  )(implicit p: Parameters): Parameters = {
    val (port_cast, dma_cast, mem_front) = config(OptionalPassKey).asInstanceOf[
      (AXI4MasterNode, Option[AXI4MasterNode], Seq[AXI4SlaveNode])
    ]
    val ap = port_cast.out(0)._1.params

    val S00_AXI = IO(
      Flipped(
        new AXI4Compat(
          MasterPortParams(
            base = 0,
            size = 1L << p(PlatformKey).frontBusAddressNBits,
            beatBytes = ap.dataBits / 8,
            idBits = ap.idBits
          )
        )
      )
    )
    AXI4Compat.connectCompatSlave(S00_AXI, port_cast.out(0)._1)

    if (withDMA) {
      val dma = IO(
        Flipped(
          new AXI4Compat(
            MasterPortParams(
              base = 0,
              size = platform.extMem.master.size,
              beatBytes = dma_cast.get.out(0)._1.r.bits.data.getWidth / 8,
              idBits = platform.asInstanceOf[PlatformHasSeparateDMA].DMAIDBits
            )
          )
        )
      )
      AXI4Compat.connectCompatSlave(dma, dma_cast.get.out(0)._1)
    }
    val mems = {
      val dram_ports = mem_front
      val M00_AXI = dram_ports.zipWithIndex.map { case (a, idx) =>
        val io = IO(AXI4Compat(a.in(0)._1.params))
        io.suggestName(s"M0${idx}_AXI")
        io
      }
      val ins = dram_ports.map(_.in(0))
      (M00_AXI zip ins) foreach { case (i, (o, _)) =>
        AXI4Compat.connectCompatMaster(
          i,
          o,
          if (platform.hasDebugAXICACHEPROT) config(DebugCacheProtSignalKey)
          else None
        )
      }
      if (platform.extMem.master.idBits > 0) {
        require(
          M00_AXI(0).rid.getWidth <= platform.extMem.master.idBits,
          s"Too many ID bits for this platform. Try reducing the\n" +
            s"prefetch length of scratchpads/readers/writers.\n" +
            s"Current width: ${M00_AXI(0).rid.getWidth}\n" +
            s"Required width: ${platform.extMem.master.idBits}"
        )
      }
    }

    val clocks = Seq.tabulate(nClocks) { i =>
      val clock = IO(Input(Clock()))
      if (i == 0)
        clock.suggestName(f"clock")
      else
        clock.suggestName(f"clock_$i")

    }

    val base_reset = if (platform.isActiveHighReset) {
      "reset"
    } else {
      "RESETn"
    }

    val resets = Seq.tabulate(nClocks) { i =>
      val reset = IO(Input(Bool()))
      if (i == 0)
        reset.suggestName(base_reset)
      else
        reset.suggestName(f"${base_reset}_$i")
    }
    config.alterPartial({
      case ClockKey => clocks
      case ResetKey =>
        if (platform.isActiveHighReset) resets else resets.map(!_)
    })
  }

  override def deriveTLSources(implicit p: Parameters): Parameters = {
    val frontInterfaceID = platform.physicalInterfaces
      .find(_.isInstanceOf[PhysicalHostInterface])
      .get
      .locationDeviceID

    DeviceContext.withDevice(frontInterfaceID) {
      val axi_master = AXI4MasterNode(
        Seq(
          AXI4MasterPortParameters(
            masters = Seq(
              AXI4MasterParameters(
                name = "S00_AXI",
                aligned = true,
                maxFlight = Some(1),
                id = IdRange(0, 1 << 16)
              )
            )
          )
        )
      )
      val fronthub =
        DeviceContext.withDevice(frontInterfaceID) {
          val fronthub =
            LazyModuleWithFloorplan(new FrontBusHub(), "zzfront6_axifronthub")
          fronthub.axi_in := axi_master
          fronthub
        }
      val (dma_node, dma_front) = if (withDMA) {
        val node = AXI4MasterNode(
          Seq(
            AXI4MasterPortParameters(
              masters = Seq(
                AXI4MasterParameters(
                  name = "S01_AXI",
                  maxFlight = Some(1),
                  aligned = true,
                  id = IdRange(0, 1 << 6)
                )
              )
            )
          )
        )
        val dma2tl = TLIdentityNode()
        DeviceContext.withDevice(frontInterfaceID) {
          dma2tl :=
            make_tl_buffer() :=
            LazyModuleWithFloorplan(new LongAXI4ToTL(64)).node :=
            AXI4UserYanker(capMaxFlight = Some(1)) :=
            AXI4IdIndexer(1) :=
            AXI4Buffer() := node
        }
        (Some(node), Some(dma2tl))
      } else (None, None)

      val rocc_xb = DeviceContext.withDevice(frontInterfaceID) {
        RoccFanout("zzfront_7roccout")
      }

      val (mem_front, mem_internal) = {
        val device = new MemoryDevice
        val mem_fronts = Seq.tabulate(platform.memoryNChannels) { channel_idx =>
          AXI4SlaveNode(
            Seq(
              AXI4SlavePortParameters(
                slaves = Seq(
                  AXI4SlaveParameters(
                    address = Seq(getAddressSet(channel_idx)),
                    resources = device.reg,
                    regionType = RegionType.UNCACHED,
                    supportsRead = TransferSizes(
                      platform.extMem.master.beatBytes,
                      platform.extMem.master.beatBytes * platform.prefetchSourceMultiplicity
                    ),
                    supportsWrite = TransferSizes(
                      platform.extMem.master.beatBytes,
                      platform.extMem.master.beatBytes * platform.prefetchSourceMultiplicity
                    ),
                    interleavedId = Some(1)
                  )
                ),
                beatBytes = platform.extMem.master.beatBytes
              )
            )
          )
        }

        val mem_internals = mem_fronts.map { fr => fr }
        (mem_fronts, mem_internals)
      }

      rocc_xb := fronthub.rocc_out
      new Config((_, _, _) => {
        case OptionalPassKey         => (axi_master, dma_node, mem_front)
        case RoccNodeKey             => rocc_xb
        case DMANodeKey              => dma_front
        case DebugCacheProtSignalKey => fronthub.module.io.cache_prot
        case BeethovenInternalMemKey => mem_internal
      })
    }
  }
}
