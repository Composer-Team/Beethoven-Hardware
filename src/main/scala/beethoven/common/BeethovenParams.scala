package beethoven

import beethoven.Platforms._
import org.chipsalliance.cde.config._
import beethoven.Platforms.FPGA.Xilinx.FPGAResources
import firrtl.options.PhaseManager
import firrtl.options.Dependency
import chisel3.stage._
import chisel3.stage.phases._
import firrtl.stage._
import firrtl._
import firrtl._

// Beethoven-system parameters
case object AcceleratorSystems extends Field[List[AcceleratorSystemConfig]]

case object DRAMBankBytes extends Field[Int]

case object BQuiet extends Field[Boolean]

case object UseConfigAsOutputNameKey extends Field[Boolean]

// Architecture parameters
case object CmdRespBusWidthBytes extends Field[Int]

case object MaxInFlightMemTxsPerSource extends Field[Int]

// Platforms that require full bi-directional IO coherence must set this to true
//case class CoherenceConfiguration(memParams: MasterPortParams, maxMemorySegments: Int)

//case object HasCoherence extends Field[Option[CoherenceConfiguration]]

// this might need to be high to expand beyond one slr

case class BeethovenIOInterface[T <: AccelCommand, R <: AccelResponse](
    coreCommand: T,
    coreResponse: R
)

trait ModuleConstructor {
  private val synthesis_test_directory = os.pwd / ".test_synth"
  // this is under construction by carson
  def estimateFPGAResources(
      constructor: () => chisel3.Module
  )(implicit p: Parameters): FPGAResources = {
    val phase = new PhaseManager(
      Seq(
        Dependency[chisel3.stage.phases.Checks],
        Dependency[chisel3.stage.phases.Elaborate],
        Dependency[chisel3.stage.phases.AddImplicitOutputFile],
        Dependency[chisel3.stage.phases.AddImplicitOutputAnnotationFile],
        Dependency[chisel3.stage.phases.Convert]
      )
    )

    phase
      .transform(
        Seq(
          ChiselGeneratorAnnotation(constructor),
          // RunFirrtlTransformAnnotation(new VerilogEmitter),
          // new TargetDirAnnotation(synthesis_test_directory.toString())
        )
      )
      .collectFirst { case EmittedVerilogCircuitAnnotation(a) =>
        a
      }
      .get
      .value

    // ChiselStage.emitVerilog(constructor())
    // generate a synthesis script for this particular module in `synthesis_test_directory`
    // use vivado_2024 to synthesize for this board part: xck26-sfvc784-2LV-c
    //    NOTE: SEE KRIA BUILD SCRIPTS in src/main/scala/beethoven/Platforms/FPGA/Xilinx/KriaPlatform.scala
    //          and src/main/resources/beethoven/FPGA/KriaSetup.ssp and the synth.ssp in the same directory
    // after synthesis, use report_utilization (I think there are options for easy-to-parse formats)
    //

    FPGAResources()
  }
}

// for verilog
case class BlackboxBuilderCustom(
    beethovenIOs: Seq[
      BeethovenIOInterface[_ <: AccelCommand, _ <: AccelResponse]
    ],
    sourcePath: os.Path,
    externalDependencies: Option[Seq[os.Path]] = None
) extends ModuleConstructor

case class BlackboxBuilderRocc() extends ModuleConstructor

case class ModuleBuilder(constructor: Parameters => AcceleratorCore)
    extends ModuleConstructor

object BeethovenConstraintHint extends Enumeration {
  val DistributeCoresAcrossSLRs, MemoryConstrained = Value
  type BeethovenConstraintHint = Value
}

case object ConstraintHintsKey extends Field[List[BeethovenConstraintHint.type]]

class WithBeethoven(
    platform: Platform,
    quiet: Boolean = false,
    useConfigAsOutputName: Boolean = false
) extends Config((site, _, _) => {
      case BQuiet             => quiet
      case PlatformKey        => platform
      case AcceleratorSystems => Seq()
      // PrefetchSourceMultiplicity must comply with the maximum number of beats
      // allowed by the underlying protocl. For AXI4, this is 256
      case CmdRespBusWidthBytes     => 4
      case UseConfigAsOutputNameKey => useConfigAsOutputName
      // Interconnect parameters
      // implementation hints
      case ConstraintHintsKey =>
        List(
          BeethovenConstraintHint.DistributeCoresAcrossSLRs,
          BeethovenConstraintHint.MemoryConstrained
        )

      // Additional device Parameters
      case MaxInFlightMemTxsPerSource => 1
      case DRAMBankBytes              => 4 * 1024
    })

object BeethovenParams {
  val SystemIDLengthKey = 4
  val CoreIDLengthKey = 10
}
