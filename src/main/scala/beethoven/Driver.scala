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

import java.util.regex._
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
  private val errorNoCR =
    "Environment variables 'BEETHOVEN_PATH' is not visible and no shell configuration file found.\n" +
      " Please define or configure IDE to see this enviornment variable\n"

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

  def beethovenRoot(): String = {
    if (System.getenv("BEETHOVEN_PATH") != null)
      return System.getenv("BEETHOVEN_PATH")
    val sh_full = System.getenv("SHELL")
    if (sh_full == null) throw new Exception(errorNoCR)
    val sh = sh_full.split("/").last
    val config = os.read(os.home / f".${sh}rc")
    val pattern = Pattern.compile("export BEETHOVEN_PATH=([a-zA-Z/.]*)")
    val matcher = pattern.matcher(config)
    if (matcher.find()) matcher.group(0).split("=")(1).strip()
    else throw new Exception(errorNoCR)
  }

  val IP_DIR = os.pwd / "ips"

  private val beethovenGenDir: String =
    beethovenRoot() + "/build/"
  val top_build_dir = Path(BeethovenBuild.beethovenGenDir)
  val hw_build_dir = top_build_dir / "hw"

  var symbolicMemoryResources: Seq[Path] = Seq.empty
  var sourceList: Seq[Path] = Seq.empty

  def addSource(p: Path): Unit = {
    sourceList = sourceList :+ p
  }

  private[beethoven] def addSymbolicResource(p: Path): Unit = {
    symbolicMemoryResources = symbolicMemoryResources :+ p
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

class BeethovenBuild(
    config: AcceleratorConfig,
    platform: Platform,
    buildMode: BuildMode = BuildMode.Synthesis,
    additional_parameter: Option[PartialFunction[Any, Any]] = None
) {
  final def main(args: Array[String]): Unit = {
    //    args.foreach(println(_))
//    println("Running with " + Runtime.getRuntime.freeMemory() + "B memory")
//    println(Runtime.getRuntime.maxMemory.toString + "B")
    BuildArgs.args = Map.from(
      args.filter(str => str.length >= 2 && str.substring(0, 2) == "-D").map { opt =>
        val pr = opt.substring(2).split("=")
        //          println(pr(0) + " " + pr(1))
        (pr(0), pr(1).toInt)
      }
    )

    os.remove.all(hw_build_dir)
    os.makeDir.all(hw_build_dir)
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

    // for carson to test
    // config.configs.foreach {
    //   a: AcceleratorSystemConfig =>
    //     a.moduleConstructor.estimateFPGAResources(a.moduleConstructor match {
    //       case ModuleBuilder(constructor) =>
    //         () => constructor(configWithBuildMode)
    //     })(configWithBuildMode)
    // }
    (new ChiselStage).execute(
      Array(
        "--target-dir",
        s"${BeethovenBuild.hw_build_dir}",
        "--target",
        "systemverilog",
        "--split-verilog"
      ),
      Seq(
        ChiselGeneratorAnnotation(() => LazyModule(new BeethovenTop()(configWithBuildMode)).module),
        FirtoolOption("--disable-all-randomization"),
        FirtoolOption(
          "--lowering-options=locationInfoStyle=wrapInAtSquareBracket,mitigateVivadoArrayIndexConstPropBug,locationInfoStyle=wrapInAtSquareBracket,disallowLocalVariables"
        )
      )
    )

    os.remove.all(hw_build_dir / "firrtl_black_box_resource_files.f")
    val allChiselGeneratedSrcs = WalkPath(hw_build_dir).filter{p =>
      val rel = p.relativeTo(hw_build_dir).toString()
      !rel.contains("verification/") && os.isFile(p)}

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
      beethoven.Generation.Annotators.UniqueMv(sourceList, hw_build_dir) :+ {
        val s = hw_build_dir / "BeethovenAllShifts.v"
        val stxts = shifts.map(a => os.read(a))
        os.write(s, stxts.mkString("\n\n"))
        shifts.foreach(os.remove(_))
        s
      }

    ConstraintGeneration.slrMappings.foreach { slrMapping =>
      crossBoundaryDisableList = crossBoundaryDisableList :+ slrMapping._1
    }
    if (crossBoundaryDisableList.nonEmpty && buildMode == BuildMode.Synthesis) {
      CrossBoundaryDisable(crossBoundaryDisableList, top_build_dir)
    }
    if (
      configWithBuildMode(PlatformKey).platformType == PlatformType.FPGA &&
      !configWithBuildMode(PlatformKey).isInstanceOf[AWSF1Platform]
    ) {
      val tc_axi = (0 until platform.extMem.nMemoryChannels) map { idx =>
        beethoven.Generation.Annotators.AnnotateXilinxInterface(
          f"M0${idx}_AXI",
          (hw_build_dir / "BeethovenTop.sv").toString(),
          XilinxInterface.AXI4
        )
        Some(f"M0${idx}_AXI")
      }
      // implies is AXI
      val tc_front = {
        beethoven.Generation.Annotators.AnnotateXilinxInterface(
          "S00_AXI",
          (hw_build_dir / "BeethovenTop.sv").toString(),
          XilinxInterface.AXI4
        )
        Some("S00_AXI")
      }

      val dma_annot = if (platform.isInstanceOf[PlatformHasDMA]) {
        beethoven.Generation.Annotators.AnnotateXilinxInterface(
          "dma",
          (hw_build_dir / "BeethovenTop.sv").toString(),
          XilinxInterface.AXI4
        )
        Some("dma")
      } else None
  
      val tcs = ((Seq(tc_front) ++ tc_axi ++ Seq(dma_annot)) filter (_.isDefined) map (_.get))
        .mkString(":")
      Annotators.AnnotateTopClock(
        f"\\(\\* X_INTERFACE_PARAMETER = \"ASSOCIATED_BUSIF $tcs \" \\*\\)",
        hw_build_dir / "BeethovenTop.sv"
      )
    }

    os.makeDir.all(top_build_dir)
    os.write.over(
      top_build_dir / "cmake_srcs.cmake",
      f"""set(SRCS ${movedSrcs.mkString("\n")}\n${chiselGeneratedSrcs.mkString(
          "\n"
        )})\n"""
    )
//    println("wrote to " + gsrc_dir / "vcs_srcs.in")
    val allSrcs =
      (chiselGeneratedSrcs.filter(!os.isDir(_)).toList ++ movedSrcs.filter(a => !os.isDir(a)).toList).filter{
        p => 
          val i = p.toString().lastIndexOf(".")
          // println(p + " " + p.toString().substring(i))
          p.toString().substring(i) match {
            case ".v" | ".sv" | ".svh" => true
            case _ => false
          }
      }
    os.write.over(
      top_build_dir / "vcs_srcs.in",
      allSrcs.mkString("\n")
    )
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
