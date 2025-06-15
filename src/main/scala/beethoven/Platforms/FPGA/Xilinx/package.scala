package beethoven.Platforms.FPGA

import beethoven.BeethovenBuild

package object Xilinx {
  def getTclMacros(): Seq[String] = {
    BeethovenBuild.postProcessorBundles.filter(_.isInstanceOf[AWS.tclMacro]).map {
      case AWS.tclMacro(cmd, _) => cmd
    }
  }
}
