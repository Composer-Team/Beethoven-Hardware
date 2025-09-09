package beethoven.Platforms.FPGA.Xilinx.Templates

import org.fusesource.scalate._

case class SynthScript(
    projectName: String,
    outputDir: String,
    fpga_part_name: String,
    board_part: String,
    clock_rate: String,
    verilog_file: String = "beethoven.v",
    top_module: String = "BeethovenTop",
    setup_ssp: String = "KriaSetup.ssp",
    precompile_dependencies: Seq[String] = Seq.empty[String],
    extra_arguments: Map[String, String] = Map()
) {
  val engine = new TemplateEngine
  engine.resourceLoader = (uri: String) => {
    val text = os.read(os.resource / "beethoven" / "FPGA" / uri)
    Some(TemplateSource.fromText(uri, text))
  }
  val environment = Map(
    "project_name" -> projectName,
    "output_dir" -> outputDir,
    "part_name" -> fpga_part_name,
    "board_part" -> board_part,
    "verilog_file" -> verilog_file,
    "top_module" -> top_module,
    "clock_rate" -> clock_rate
  ) ++ extra_arguments
  lazy val setup = engine.layout(setup_ssp, environment)
  lazy val ip_script = precompile_dependencies.mkString("\n")
  lazy val run = engine.layout("synth.ssp", environment)
}
