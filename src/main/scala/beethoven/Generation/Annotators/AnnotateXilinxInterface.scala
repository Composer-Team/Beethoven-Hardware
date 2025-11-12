package beethoven.Generation.Annotators

import beethoven.Generation.Annotators.AnnotateXilinxInterface.XilinxInterface.{
  ACE,
  XilinxInterface
}
import os._
object AnnotateXilinxInterface {
  object XilinxInterface extends Enumeration {
    val ACE, AXI4, AHB = Value
    type XilinxInterface = Value
  }
  private def perform_sed(cmd: Seq[String], fname: String): Unit = {
    val sedCmd = Seq("sed") ++ get_sed_inline_opt() ++ get_sed_z_opt() ++ Seq(
      "-E",
      cmd.mkString(";"),
      fname
    )
    os.proc(sedCmd).call()
  }

  def apply(prefix: String, fname: String, interface: XilinxInterface): Unit = {
    val interfaceName = interface match {
      case XilinxInterface.ACE  => "xilinx.com:interface:acemm_rtl:1.0"
      case XilinxInterface.AXI4 => "xilinx.com:interface:aximm_rtl:1.0"
      case XilinxInterface.AHB  => "xilinx.com:interface:ahblite_rtl:1.0"
    }
    val busName = prefix.toUpperCase()
    val width_info = "\\[[0-9]*:[0-9]*\\]"
    val copy_directionality_info_cmd =
      s"s/ *(input|output) *($width_info) *($prefix)([a-zA-Z_0-9]+),\\s*\\/\\/[ @\\[a-zA-Z\\/\\.:0-9\\-]*\\]\\n *$prefix/" +
        s"  \\1 \\2 \\3\\4,\\n  \\1 \\2 $prefix/g"
    val copy_directionality_info_cmd_1wide =
      s"s/ *(input|output) *($prefix)([a-zA-Z_0-9]+),\\s*\\/\\/[ @\\[a-zA-Z\\/\\.:0-9\\-]*\\]\\n *$prefix/" +
        s"  \\1 \\2\\3,\\n  \\1 $prefix/g"

    val ensure_clock_input = "s/\\n *clock/\\n  input clock/"
    val ensure_areset_input =
      Seq("s/\\n *ARESETn/\\n  input ARESETn/", "s/\\n *areset/\\n  input areset/")
    val add_annotation_cmd =
      s"s/\\n( *)(input|output) *($width_info) *${prefix}_([a-zA-Z_]*),/" +
        s"\\n  (\\* X_INTERFACE_INFO = \"$interfaceName $busName \\4\" \\*\\)\\0/g"
    val add_annotation_cmd_1wide =
      s"s/\\n( *)(input|output) *${prefix}_([a-zA-Z_]*),/" +
        s"\\n  (\\* X_INTERFACE_INFO = \"$interfaceName $busName \\3\" \\*\\)\\0/g"

    // println(add_annotation_cmd)
    /*
s/ *(input|output)(.*)(M00_AXI)(.*),.*\n *(M00_AXI)/  \1\2\3\4,\n  \1\2\5/
s/ *(input|output)(.*)(S00_AXI)(.*),\n *(S00_AXI)/  \1\2\3\4,\n  \1\2\5/
s/ *(input|output)(.*)(dma)(.*),\n *(dma)/  \1\2\3\4,\n  \1\2\5/ */
//     then, remove the repeated modules from the source
//     join sedCMds with ;
    perform_sed(Seq(copy_directionality_info_cmd, copy_directionality_info_cmd_1wide), fname)
    perform_sed(ensure_areset_input ++ Seq(ensure_clock_input), fname)
    perform_sed(Seq(add_annotation_cmd, add_annotation_cmd_1wide), fname)
    (os.pwd / "tmp.v").toString()
  }

}
