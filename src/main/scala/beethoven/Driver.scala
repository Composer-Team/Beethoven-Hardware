package beethoven

import beethoven.BeethovenBuild._
import beethoven.Floorplanning.ConstraintGeneration
import beethoven.Generation.Annotators.AnnotateXilinxInterface.XilinxInterface
import beethoven.Generation.Annotators.{CrossBoundaryDisable, WalkPath}
import beethoven.Generation.{Annotators, vcs}
import beethoven.Platforms.FPGA.Xilinx.AWS.AWSF1Platform
import beethoven.Platforms._
import org.chipsalliance.diplomacy._
import firrtl.options.PhaseManager.PhaseDependency
import firrtl.options._
import circt.stage._
// import firrtl.stage.RunFirrtlTransformAnnotation
// import freechips.rocketchip.stage._
import os._

// import firrtl.transforms.DeadCodeElimination
import chisel3.stage.ChiselGeneratorAnnotation
import beethoven.Systems.BeethovenTop
import chisel3.stage.phases.Elaborate
import chisel3.stage.phases.Convert
import firrtl.AnnotationSeq
import circt.stage.SplitVerilog
import chisel3.emitVerilog

class BeethovenChipStage extends ChiselStage {
  // override val shell = new Shell("beethoven-compile")
  // val targets: Seq[PhaseDependency] = Seq(
  //   Dependency[beethoven.Generation.Stage.PreElaborationPass],
  //   Dependency[chisel3.stage.phases.Checks],
  //   Dependency[chisel3.stage.phases.Convert], // convert chirrtl to firrtl
  //   Dependency[firrtl.stage.phases.Compiler]
  // )

  // private val pm = new PhaseManager(targets)

  // override def run(annotations: AnnotationSeq): AnnotationSeq =
  //   pm.transform(annotations)
}

object BeethovenBuild {
  private var crossBoundaryDisableList: Seq[String] = Seq.empty

  def disableCrossBoundaryOptimizationForModule(moduleName: String): Unit = {
    crossBoundaryDisableList = crossBoundaryDisableList :+ moduleName
  }

  private def filterFIRRTL(path: Path): Unit = {
    os.proc(
      Seq(
        "sed",
        "-i.backup",
        "-e",
        "1d",
        "-e",
        "/printf/d",
        "-e",
        "/assert/d",
        path.toString()
      )
    ).call()
  }

  private[beethoven] var partitionModules: Seq[String] = Seq("BeethovenTop")
  var separateCompileCells: Seq[String] = Seq.empty
  def requestModulePartition(moduleName: String): Unit = {
    partitionModules = (partitionModules :+ moduleName).distinct
  }

  def requestSeparateCompileCell(cellName: String): Unit =
    separateCompileCells = (separateCompileCells :+ cellName).distinct

  def getPartitions: Seq[String] = partitionModules

  private[beethoven] def addSource(): Unit = {}

  var postProcessorBundles: Seq[Any] = Seq.empty
  def addPostProcessorBundle(bundle: Any): Unit = {
    postProcessorBundles = postProcessorBundles :+ bundle
  }

  // -- paths machinery -------------------------------------------------------

  private var optPaths: Option[BeethovenPaths] = None

  /** Resolved on-disk locations for this run, set by [[run]] before any
    * generator code reads it. Errors clearly if accessed before initialization.
    */
  def paths: BeethovenPaths = optPaths.getOrElse(
    sys.error(
      "BeethovenBuild.paths accessed before initialization. " +
        "Invoke beethoven.cli.Run (toml-driven) or BeethovenBuild.run(...) first."
    )
  )

  private[beethoven] def setPaths(p: BeethovenPaths): Unit = {
    optPaths = Some(p)
  }

  /** Vendor IP cache (`target/.cache/ips/`). Holds Xilinx `.xci` files, one
    * per generated IP. Vivado TCL references vendor IPs by name from the
    * Vivado project's local `ips/` (created during synth), not by absolute
    * path here, so the cache root is invisible to TCL.
    */
  def IP_DIR: Path = paths.cacheRoot / "ips"

  var symbolicMemoryResources: Seq[Path] = Seq.empty
  var sourceList: Seq[Path] = Seq.empty

  def addSource(p: Path): Unit = {
    sourceList = sourceList :+ p
  }

  private[beethoven] def addSymbolicResource(p: Path): Unit = {
    symbolicMemoryResources = symbolicMemoryResources :+ p
  }

  // -- run flow --------------------------------------------------------------

  /** The build flow. Invoked by [[beethoven.cli.Run]] (the framework's sbt
    * `mainClass` for toml-driven projects) once the manifest has been parsed
    * and the platform/config/paths resolved.
    */
  def run(
      config: AcceleratorConfig,
      platform: Platform,
      buildMode: BuildMode,
      paths: BeethovenPaths,
      userArgs: Map[String, Int] = Map.empty,
      additional_parameter: Option[PartialFunction[Any, Any]] = None
  ): Unit = {
    setPaths(paths)
    BuildArgs.args = userArgs

    val rtlRoot = paths.rtlRoot
    val hwDir   = rtlRoot / "hw"

    os.remove.all(hwDir)
    os.makeDir.all(hwDir)
    val configWithBuildMode = {
      val w = new WithBeethoven(platform = platform).alterPartial {
        case BuildModeKey       => buildMode
        case AcceleratorSystems => config.configs
      }
      additional_parameter match {
        case Some(f) => w.alterPartial(f)
        case None    => w
      }
    }
    beethoven.platform(configWithBuildMode).platformCheck()

    (new ChiselStage).execute(
      Array(
        "--target-dir",
        s"$hwDir",
        "--target",
        "systemverilog",
        "--split-verilog"
      ),
      Seq(
        ChiselGeneratorAnnotation(() => LazyModule(new BeethovenTop()(configWithBuildMode)).module),
        FirtoolOption("--disable-all-randomization"),
        FirtoolOption(
          "--lowering-options=locationInfoStyle=wrapInAtSquareBracket," +
            "mitigateVivadoArrayIndexConstPropBug,locationInfoStyle=wrapInAtSquareBracket," +
            "disallowLocalVariables"
        )
        ,FirtoolOption("-O=debug")
      )
    )

    os.remove.all(hwDir / "firrtl_black_box_resource_files.f")
    val allChiselGeneratedSrcs = WalkPath(hwDir).filter { p =>
      val rel = p.relativeTo(hwDir).toString()
      !rel.contains("verification/") && os.isFile(p)
    }

    val chiselGeneratedSrcs = allChiselGeneratedSrcs
      .filter(a => !a.toString().contains("ShiftReg") && !a.toString().contains("Queue"))
      .filter(a => !a.toString().contains("txt"))
    val shifts = allChiselGeneratedSrcs.filter(a =>
      a.toString().contains("ShiftReg") || a.toString().contains("Queue")
    )
    config.configs.foreach { conf =>
      conf.moduleConstructor match {
        case a: BlackboxBuilderCustom =>
          a.externalDependencies.getOrElse(Seq()).foreach { path =>
            sourceList = sourceList.appended(path)
          }
        case _ => ;
      }
    }
    val movedSrcs =
      beethoven.Generation.Annotators.UniqueMv(sourceList, hwDir) :+ {
        val s = hwDir / "BeethovenAllShifts.v"
        val stxts = shifts.map(a => os.read(a))
        os.write(s, stxts.mkString("\n\n"))
        shifts.foreach(os.remove(_))
        s
      }

    ConstraintGeneration.slrMappings.foreach { slrMapping =>
      crossBoundaryDisableList = crossBoundaryDisableList :+ slrMapping._1
    }
    if (crossBoundaryDisableList.nonEmpty && buildMode == BuildMode.Synthesis) {
      CrossBoundaryDisable(crossBoundaryDisableList, rtlRoot)
    }
    if (
      configWithBuildMode(PlatformKey).platformType == PlatformType.FPGA &&
      !configWithBuildMode(PlatformKey).isInstanceOf[AWSF1Platform]
    ) {
      val tc_axi = (0 until platform.extMem.nMemoryChannels) map { idx =>
        beethoven.Generation.Annotators.AnnotateXilinxInterface(
          f"M0${idx}_AXI",
          (hwDir / "BeethovenTop.sv").toString(),
          XilinxInterface.AXI4
        )
        Some(f"M0${idx}_AXI")
      }
      // implies is AXI
      val tc_front = {
        beethoven.Generation.Annotators.AnnotateXilinxInterface(
          "S00_AXI",
          (hwDir / "BeethovenTop.sv").toString(),
          XilinxInterface.AXI4
        )
        Some("S00_AXI")
      }

      val dma_annot = if (platform.isInstanceOf[PlatformHasDMA]) {
        beethoven.Generation.Annotators.AnnotateXilinxInterface(
          "dma",
          (hwDir / "BeethovenTop.sv").toString(),
          XilinxInterface.AXI4
        )
        Some("dma")
      } else None

      val tcs = ((Seq(tc_front) ++ tc_axi ++ Seq(dma_annot)) filter (_.isDefined) map (_.get))
        .mkString(":")
      Annotators.AnnotateTopClock(
        f"\\(\\* X_INTERFACE_PARAMETER = \"ASSOCIATED_BUSIF $tcs \" \\*\\)",
        hwDir / "BeethovenTop.sv"
      )
    }

    os.makeDir.all(rtlRoot)
    if (buildMode == BuildMode.Simulation)
      vcs.HarnessGenerator.generateHarness()(configWithBuildMode)

    buildMode match {
      case BuildMode.Synthesis =>
        platform match {
          case pwpp: Platform with HasPostProccessorScript =>
            pwpp.postProcessorMacro(
              configWithBuildMode,
              movedSrcs ++ chiselGeneratedSrcs
            )
          case _ => ;
        }
      case _ => ;
    }
  }
}

object BuildArgs {
  private[beethoven] var args: Map[String, Int] = Map.empty
}

abstract class BuildMode

object BuildMode {
  case object Synthesis extends BuildMode

  case object Simulation extends BuildMode
}

