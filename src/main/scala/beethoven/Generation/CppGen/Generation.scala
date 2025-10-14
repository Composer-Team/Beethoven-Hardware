package beethoven.Generation.CppGen

import org.chipsalliance.cde.config.Parameters
import chisel3.util._
import beethoven.BuildMode.Simulation
import beethoven.Generation.CPP.CommandParsing.customCommandToCpp
import beethoven.Generation.CPP.TypeParsing._
import beethoven.Generation.CppGeneration._
import beethoven.BeethovenParams.{CoreIDLengthKey, SystemIDLengthKey}
import beethoven.Generation.CPP.safe_join
import beethoven.Platforms.{BuildModeKey, PlatformHasDMA}
import beethoven.Systems._
import beethoven._
import os.Path

import java.io.FileWriter

object Generation {
  def genCPPHeader(top: BeethovenTop)(implicit p: Parameters): Unit = {
    val acc = p(AcceleratorSystems)
    // we might have multiple address spaces...

    val actualChannels = platform.memoryNChannels
    val (allocator, addrBits) = {
      (
        s"""
           |#ifdef SIM
           |#define HW_IS_RESET_ACTIVE_HIGH ${if (platform.isActiveHighReset) 1
          else 0}
           |#define NUM_DDR_CHANNELS ${actualChannels}
           |#endif
           |#define ALLOCATOR_SIZE_BYTES (0x${platform.physicalMemoryBytes.toHexString}L)
           |${if (!platform.hasDiscreteMemory) "#ifdef SIM" else ""}
           |""".stripMargin + "#define BEETHOVEN_USE_CUSTOM_ALLOC\n" +
          (if (!platform.hasDiscreteMemory) "#endif" else ""),
        s"const uint8_t beethovenNumAddrBits = ${log2Up(platform.extMem.master.size)};"
      )
    }
    val defines = safe_join(user_defs map { deff =>
      s"const ${deff.ty} ${deff.name} = ${deff.value};"
    })
    val enums = safe_join(user_enums map enumToCpp)
    val cpp_defs = safe_join(user_cpp_defs map { ppd =>
      s"#define ${ppd.ty} ${ppd.value}"
    })
    val system_ids = safe_join(
      acc.filter(_.canReceiveSoftwareCommands) map { tup =>
        s"const uint8_t ${tup.name}_ID = ${acc.indexWhere(_.name == tup.name)};"
      }
    )
    val hooks_dec_def = hook_defs map customCommandToCpp _
    val commandDeclarations = safe_join(hooks_dec_def.map(_._1))
    val commandDefinitions = safe_join(hooks_dec_def.map(_._2))
    val (idLengths, dma_def, mem_def) = {
      val addrSet = BeethovenTop.getAddressSet(0)

      // this next stuff is just for simulation
      def getVerilatorDtype(width: Int): String = {
        width match {
          case x if x <= 8  => "CData"
          case x if x <= 16 => "SData"
          case x if x <= 32 => "IData"
          case x if x <= 64 => "QData"
          case _            => "ERROR"
        }
      }
      def getSignedCIntType(width: Int): String = {
        if (width <= 8) "int8_t"
        else if (width <= 16) "int16_t"
        else if (width <= 32) "int32_t"
        else if (width <= 64) "int64_t"
        else throw new Exception("Type is too big")
      }
      def getUnsignedCIntType(width: Int): String =
        "u" + getSignedCIntType(width)
      val addrWid = log2Up(platform.extMem.master.size)
      (
        s"""
           |static const uint64_t addrMask = 0x${addrSet.mask.toLong.toHexString};
           |""".stripMargin,
        if (
          platform.isInstanceOf[PlatformHasDMA] &&
          p(BuildModeKey) != Simulation
        ) "#define " + (platform.asInstanceOf[PlatformHasDMA].DMAisLite match {
          case true  => "BEETHOVEN_HAS_DMA_LITE"
          case false => "BEETHOVEN_HAS_DMA_AXI4"
        })
        else "", {
          if (platform.memoryNChannels > 0) {
            val idDtype = getVerilatorDtype(platform.extMem.master.idBits)
            f"""
             |#ifdef SIM
             |${if (platform.extMem.master.beatBytes < 8)
                "#define SIM_SMALL_MEM"
              else ""}
             |#ifdef VERILATOR
             |#include <verilated.h>
             |using BeethovenFrontBusAddr_t = ${getUnsignedCIntType(
                platform.frontBusAddressNBits
              )};
             |using BeethovenMemIDDtype=$idDtype;
             |${platform match {
                case pWithDMA: PlatformHasDMA =>
                  s"using BeethovenDMAIDtype=${getVerilatorDtype(pWithDMA.DMAIDBits)};";
                case _ => ""
              }}
             |#endif
             |#define DEFAULT_PL_CLOCK ${platform.clockRateMHz}
             |#define DATA_BUS_WIDTH ${platform.extMem.master.beatBytes * 8}
             |#endif
             |""".stripMargin
          } else {
            f"""
             |#ifdef SIM
             |#ifdef VERILATOR
             |#include <verilated.h>
             |using BeethovenFrontBusAddr_t = ${getUnsignedCIntType(
                platform.frontBusAddressNBits
              )};
             |#endif
             |#endif
             1|
             |#define DATA_BUS_WIDTH ${platform.extMem.master.beatBytes * 8}
             |#define DEFAULT_PL_CLOCK ${platform.clockRateMHz}
             |
             |""".stripMargin
          }
        }
      )
    }
    val mmio_addr =
      "const uint64_t BeethovenMMIOOffset = 0x" + platform.frontBusBaseAddress.toHexString + "LL;"

    os.makeDir.all(BeethovenBuild.top_build_dir / "beethoven")
    val header = new FileWriter(
      (BeethovenBuild.top_build_dir / "beethoven_hardware.h").toString()
    )
    header.write(f"""
         |// Automatically generated header for Beethoven
         |
         |#ifdef BAREMETAL
         |#include <beethoven_baremetal/allocator/alloc_baremetal.h>
         |#else
         |#include <beethoven/allocator/alloc.h>
         |#endif
         |#include <beethoven/rocc_cmd.h>
         |#include <cinttypes>
         |#ifndef BAREMETAL
         |#include <optional>
         |#include <cassert>
         |#endif
         |
         |#ifndef BEETHOVEN_ALLOCATOR_GEN
         |#define BEETHOVEN_ALLOCATOR_GEN
         |#define AXIL_BUS_WIDTH ${platform.frontBusBeatBytes * 8}
         |#define XLEN 64
         |// allocator declarations backends that do not have discrete memory or for simulator
         |$allocator
         |// address bits used by FPGA
         |$addrBits
         |// const defines declared by user cores
         |$defines
         |// enums declared by user cores
         |$enums
         |// C Preprocessor definitions declared by user cores
         |$cpp_defs
         |// MMIO address + field offsets
         |static const uint8_t system_id_bits = $SystemIDLengthKey;
         |static const uint8_t core_id_bits = $CoreIDLengthKey;
         |extern beethoven::beethoven_pack_info pack_cfg;
         |$mmio_addr
         |// IDs to access systems directly through RoCC interface
         |$system_ids
         |// Custom command interfaces
         |$commandDeclarations
         |// misc
         |$idLengths
         |$dma_def
         |// Verilator support
         |$mem_def
         |#endif
         |//
  """.stripMargin)
    header.close()

    val src = new FileWriter(
      (BeethovenBuild.top_build_dir / "beethoven_hardware.cc").toString()
    )
    src.write(f"""
         |#include "beethoven_hardware.h"
         |
         |beethoven::beethoven_pack_info pack_cfg(system_id_bits, core_id_bits);
         |$commandDefinitions
         |""".stripMargin)
    src.close()
  }

}
