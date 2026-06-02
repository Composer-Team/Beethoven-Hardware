package beethoven.Platforms.FPGA.Xilinx.AWS

import beethoven.Platforms.FPGA.Xilinx
import beethoven.Platforms.PlatformType.PlatformType
import beethoven.Platforms._
import beethoven.Protocol.FrontBus.{AXIFrontBusProtocol, FrontBusProtocol}
import beethoven._
import org.chipsalliance.cde.config._
import os.Path
import beethoven.common.tclMacro

import java.nio.file.StandardCopyOption

object AWSF2Platform {
  private val resourceRoot = "/beethoven/FPGA/AWS/F2"

  private val buildScriptResources = Seq(
    "aws_build_dcp_from_cl.py",
    "aws_clock_properties.tcl",
    "aws_gen_clk_constraints.tcl",
    "build_all.tcl",
    "build_level_1_cl.tcl",
    "check_ddr_bram.tcl",
    "ddr_io_train.tcl",
    "encrypt.tcl",
    "placement_fix_v22_1.tcl",
    "synth_cl_footer.tcl",
    "synth_cl_header.tcl",
    "vivado_keyfile.txt",
    "vivado_keyfile_2024_1.txt",
    "vivado_vhdl_keyfile.txt",
    "vivado_vhdl_keyfile_2024_1.txt"
  )

  private val constraintResources = Seq(
    "bitstream_physical.xdc",
    "cl_ddr_timing_aws.xdc",
    "cl_pins.xdc",
    "mmcm_cascade.xdc",
    "small_shell_level_1_fp_cl.xdc",
    "xdma_shell_level_1_fp_cl.xdc"
  )

  private def copyResource(relPath: String, dst: os.Path): Unit = {
    os.makeDir.all(dst / os.up)
    val stream = Option(getClass.getResourceAsStream(s"$resourceRoot/$relPath"))
      .getOrElse(sys.error(s"Missing AWS F2 resource: $relPath"))
    try {
      java.nio.file.Files.copy(
        stream,
        dst.toNIO,
        StandardCopyOption.REPLACE_EXISTING
      )
    } finally {
      stream.close()
    }
  }

  private def writeBuildWrapper(packageRoot: os.Path): Unit = {
    val wrapper = packageRoot / "build_beethoven_f2.sh"
    os.write.over(
      wrapper,
      """#!/usr/bin/env bash
        |set -euo pipefail
        |
        |AWS_FPGA_REPO_DIR="${BEETHOVEN_AWS_FPGA_REPO_DIR:-$HOME/aws-fpga}"
        |export AWS_FPGA_REPO_DIR
        |
        |if [[ ! -d "$AWS_FPGA_REPO_DIR" ]]; then
        |  git clone --branch f2 https://github.com/aws/aws-fpga.git "$AWS_FPGA_REPO_DIR"
        |fi
        |
        |# hdk_setup.sh sets HDK_SHELL_DIR, HDK_SHELL_DESIGN_DIR, HDK_IP_SRC_DIR,
        |# HDK_BD_SRC_DIR, and the other AWS F2 Vivado build environment variables.
        |# It may download AWS shell collateral the first time it runs.
        |pushd "$AWS_FPGA_REPO_DIR" >/dev/null
        |source hdk_setup.sh
        |popd >/dev/null
        |
        |SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
        |export CL_DIR="$SCRIPT_DIR"
        |
        |cd "$CL_DIR/build/scripts"
        |cmd=(./aws_build_dcp_from_cl.py -c cl_beethoven_top --mode small_shell "$@")
        |printf 'Executing:'
        |printf ' %q' "${cmd[@]}"
        |printf '\n'
        |"${cmd[@]}"
        |""".stripMargin
    )
    os.proc("chmod", "+x", wrapper.toString()).call()
  }

  private def copyAwsBuildResources(packageRoot: os.Path): Unit = {
    val scriptsDir = packageRoot / "build" / "scripts"
    val constraintsDir = packageRoot / "build" / "constraints"
    buildScriptResources.foreach { f =>
      copyResource(s"build/scripts/$f", scriptsDir / f)
    }
    os.proc("chmod", "+x", (scriptsDir / "aws_build_dcp_from_cl.py").toString()).call()
    constraintResources.foreach { f =>
      copyResource(s"build/constraints/$f", constraintsDir / f)
    }
    copyResource("design/cl_id_defines.vh", packageRoot / "design" / "cl_id_defines.vh")
  }

}

class AWSF2Platform(val remoteUsername: String = "ubuntu")
    extends Platform
    with HasPostProccessorScript
    with PlatformHasDMA
    with HasXilinxMem
    with AWSPlatform {

  override val memoryNChannels: Int = 1
  override val platformType: PlatformType = PlatformType.FPGA
  override val hasDiscreteMemory: Boolean = true

  override val frontBusBaseAddress: Long = 0
  override val frontBusAddressNBits: Int = 32
  override val frontBusAddressMask: Long = 0xffffL
  override val frontBusBeatBytes: Int = 4
  override val frontBusProtocol: FrontBusProtocol = new AXIFrontBusProtocol

  override val physicalMemoryBytes: Long = 0x400000000L
  override val memorySpaceAddressBase: Long = 0x0
  override val memorySpaceSizeBytes: BigInt = BigInt(2).pow(64)
  // println(memorySpaceSizeBytes)
  override val memoryControllerIDBits: Int = 16
  override val memoryControllerBeatBytes: Int = 64

  override val prefetchSourceMultiplicity: Int = 64

  override val isActiveHighReset: Boolean = false

  override val DMAIDBits: Int = 16
  override val DMABusWidthBytes: Int = 64
  override val DMAisLite: Boolean = false

  // SDA
  // override val DMAIDBits: Int = 0
  // override val DMABusWidthBytes: Int = 4
  // override val DMAisLite: Boolean = true

  override val clockRateMHz: Int = 250

  override val defaultReadTXConcurrency = 8
  override val defaultWriteTXConcurrency: Int = defaultReadTXConcurrency

  override def postProcessorMacro(c: Parameters, paths: Seq[Path]): Unit = {
    if (c(BuildModeKey) == BuildMode.Synthesis) {
      val aws_dir = BeethovenBuild.paths.rtlRoot / "aws"
      val packageRoot = aws_dir / "cl_beethoven_top"
      val design_dir = packageRoot / "design"
      val scripts_dir = packageRoot / "build" / "scripts"
      val constraints_dir = packageRoot / "build" / "constraints"
      val top_file = design_dir / "beethoven.sv"

      os.remove.all(packageRoot)
      os.makeDir.all(design_dir)
      os.makeDir.all(scripts_dir)
      os.makeDir.all(constraints_dir)

      os.proc("touch", top_file.toString()).call()
      Shell.write(
        (BeethovenBuild.paths.rtlRoot / "hw") / "cl_beethoven_top.sv",
        withDMA = Shell.DMAType.ViaDMA
      )(c)
      Shell.write_header(
        (BeethovenBuild.paths.rtlRoot / "hw") / "cl_beethoven_top_defines.vh"
      )(c)
      os.write.over(
        BeethovenBuild.paths.rtlRoot / "combined_pnr.xdc",
        """
          |source ${CL_DIR}/build/constraints/small_shell_level_1_fp_cl.xdc
          |source ${CL_DIR}/build/scripts/user_constraints.xdc
          |""".stripMargin
      )

      os.copy.over((BeethovenBuild.paths.rtlRoot / "hw"), design_dir)
      os.move(design_dir / "BeethovenTop.sv", top_file)
      os.walk(BeethovenBuild.paths.rtlRoot, followLinks = false, maxDepth = 1)
        .foreach(p =>
          if (
            p.last.endsWith(".cc") || p.last.endsWith(".h") || p.last
              .endsWith(".xdc")
          ) {
//            println("copying " + p + " to " + design_dir / p.last)
            os.copy.over(p, design_dir / p.last)
          }
        )
      AWSF2Platform.copyAwsBuildResources(packageRoot)
      val hdl_srcs = os
        .walk(design_dir)
        .filter(p =>
          (p.last.endsWith(".v") || p.last.endsWith(".sv")) && !(p.last
            .contains("VCS"))
        )
        .map { fname =>
          fname.relativeTo(design_dir)
        }
      os.write.over(
        design_dir / "src_list.tcl",
        f"set hdl_sources [list ${hdl_srcs.mkString(" \\\n")} ]"
      )

      // write ip tcl
      val ip_tcl = BeethovenBuild.paths.rtlRoot / "aws" / "ip.tcl"
      val ip_cmds = BeethovenBuild.postProcessorBundles
        .filter(_.isInstanceOf[tclMacro])
        .map(_.asInstanceOf[tclMacro].cmd)
        .mkString("\n")
      os.write.over(
        ip_tcl,
        s"""
          |set ipDir ips
          |exec rm -rf $$ipDir/*
          |exec mkdir -p $$ipDir
          |${ip_cmds}
          |update_compile_order -fileset sources_1
          |""".stripMargin
      )
      Xilinx.AWS.SynthScript.write(
        scripts_dir / "synth_cl_beethoven_top.tcl"
      )
      os.copy.over(BeethovenBuild.paths.rtlRoot / "user_constraints.xdc", scripts_dir / "user_constraints.xdc")
      os.write.over(constraints_dir / "cl_synth_user.xdc", "")
      os.write.over(constraints_dir / "cl_timing_user.xdc", "")
      os.write.over(
        constraints_dir / "small_shell_cl_pnr_user.xdc",
        os.read(constraints_dir / "small_shell_level_1_fp_cl.xdc") + "\n" +
          os.read(scripts_dir / "user_constraints.xdc")
      )
      AWSF2Platform.writeBuildWrapper(packageRoot)

      println(s"AWS F2 package generated at $packageRoot")
      println(s"Upload it with: rsync -avz $packageRoot/ $remoteUsername@<host>:~/cl_beethoven_top/")
    }
  }

  override val physicalInterfaces: List[PhysicalInterface] = List(
    PhysicalHostInterface(1),
    PhysicalMemoryInterface(2, 0)
  )
  override val physicalConnectivity: List[(Int, Int)] = List((0, 1), (1, 2))

  override val physicalDevices: List[DeviceConfig] = List(
    DeviceConfig(0, "pblock_CL_SLR0"),
    DeviceConfig(1, "pblock_CL_SLR1"),
    DeviceConfig(2, "pblock_CL_SRL2")
  )

  /** We won't _fail_ if we run out of memory, but there will be a warning and the memories will no
    * longer be annotated with a specific memory type (e.g., URAM/BRAM). This should give Vivado the
    * freedom it needs to potentially not fail placement
    */
  // 320 * (2/3) = 212
  // try to only use up to 80% (Xilinx Recommendation)
  override val nURAMs: Map[Int, Int] = Map.from(
    List((0, 230), (1, 204), (2, 256))
  ) // 960 (320 per) but try not to get too close to overallocation
  override val nBRAMs: Map[Int, Int] = Map.from(
    List((0, 480), (1, 412), (2, 537))
  ) // 2160 (720 per) but the shell takes about 30%

  override val net_intraDeviceXbarLatencyPerLayer: Int = 1
  override val net_intraDeviceXbarTopLatency: Int = 2
  override val net_fpgaSLRBridgeLatency: Int = 1

  override def placementAffinity: Map[Int, Double] =
    Map.from(Seq((0, 1.16), (1, 0.8), (2, 1.3)))
}

class AWSF2XDMAWorkarounds
    extends AcceleratorConfig(
      List(new DMAHelperConfig, new MemsetHelperConfig(4))
    )
