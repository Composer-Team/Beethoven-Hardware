package beethoven.Platforms.FPGA.Xilinx

import beethoven.BeethovenBuild
import beethoven.common.tclMacro
import beethoven.platform
import chipsalliance.rocketchip.config.Parameters

case class SynthSet(
    setup: String,
    synth: String,
    impl: String,
    additional: Map[String, String] = Map()
) {
  def write_to_dir(
      dir: os.Path,
      setup_en: Boolean = true,
      synth_en: Boolean = true,
      impl_en: Boolean = true
  ) {
    if (os.exists(dir))
      assert(os.isDir(dir))
    else
      os.makeDir.all(dir)
    if (setup_en)
      os.write.over(dir / "0_setup.tcl", setup)
    if (synth_en)
      os.write.over(dir / "1_synth.tcl", synth)
    if (impl_en)
      os.write.over(dir / "2_impl.tcl", impl)
    additional.foreach { case (f, str) =>
      os.write.over(dir / f, str)
    }
  }
}

object SynthScript {
  def apply(
      project_name: String,
      output_dir: String,
      precompile_dependencies: Seq[String] = Seq(),
      part_name: String,
      board_part: String,
      top_module: String = "BeethovenTop",
      ip_inline: Boolean = true
  )(implicit p: Parameters): SynthSet = {
    val ip_cmds = BeethovenBuild.postProcessorBundles
      .filter(_.isInstanceOf[tclMacro])
      .map(_.asInstanceOf[tclMacro].cmd)
      .mkString("\n")
    val ip_str =
      f"""#################################################################
########################## COMPILE IPs ##########################
#################################################################
${ip_cmds}
#################################################################
#################################################################
"""
    SynthSet(
      setup = f"""
# Script for compiling top-level modules to the Kria KV260 board
exec rm -rf ${output_dir}
create_project ${project_name} ${output_dir} -part ${part_name} -force
exec rm -rf ips
exec mkdir -p ips
${if (ip_inline) ip_str else ""}
puts "Adding board files from ${BeethovenBuild.board_files_dir.toString}"
set_param board.repoPaths {${BeethovenBuild.board_files_dir.toString}}
${if (board_part.strip()!="") f"set_property board_part ${board_part} [current_project]" else ""}

create_bd_design "design_1"

# Add Zynq MPSoC
startgroup
create_bd_cell -type ip -vlnv xilinx.com:ip:zynq_ultra_ps_e soc
endgroup
apply_bd_automation -rule xilinx.com:bd_rule:zynq_ultra_ps_e -config {apply_board_preset "1" } [get_bd_cells soc]
set_property -dict [list \\
    CONFIG.PSU__USE__M_AXI_GP1 {0} \\
    CONFIG.PSU__USE__S_AXI_GP2 {1} \\
    CONFIG.PSU__SAXIGP2__DATA_WIDTH {32} \\
    CONFIG.PSU__FPGA_PL0_ENABLE {1} \\
    CONFIG.PSU__CRL_APB__PL0_REF_CTRL__FREQMHZ {${platform.clockRateMHz.toString}} \\
] [get_bd_cells soc]
add_files ../hw/
update_compile_order -fileset sources_1
create_bd_cell -type module -reference ${top_module} top

set my_bd [exec find ${output_dir} -name "design_1.bd"]

connect_bd_net [get_bd_pins top/RESETn] [get_bd_pins soc/pl_resetn0]

# Connect together AXI4
startgroup
apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config { Clk_master {Auto} Clk_slave {Auto} \\
  Clk_xbar {Auto} Master {/soc/M_AXI_HPM0_FPD} Slave {/top/S00_AXI} ddr_seg {Auto}             \\
  intc_ip {New AXI SmartConnect} master_apm {0}} [get_bd_intf_pins top/S00_AXI]

apply_bd_automation -rule xilinx.com:bd_rule:axi4 -config { Clk_master {Auto} Clk_slave {Auto} \\
  Clk_xbar {Auto} Master {/top/M00_AXI} Slave {/soc/S_AXI_HP0_FPD} ddr_seg {Auto}              \\
  intc_ip {New AXI SmartConnect} master_apm {0}} [get_bd_intf_pins soc/S_AXI_HP0_FPD]

# Make wrapper and setup build environment
make_wrapper -files [get_files [exec find ${output_dir} -name "design_1.bd" ] ] -top
add_files -norecurse [exec find ${output_dir} -name "design_1_wrapper.v" ]
set_property top design_1_wrapper [current_fileset]
update_compile_order -fileset sources_1

# Make it so that we can just run synth_design and not have to deal with out-of-context IPs
reset_target all [get_files $$my_bd]
export_ip_user_files -of_objects [get_files $$my_bd] -sync -no_script -force -quiet
set_property synth_checkpoint_mode None [get_files $$my_bd]
generate_target all [get_files $$my_bd]
export_ip_user_files -of_objects [get_files $$my_bd] -no_script -sync -force -quiet

# Print out retiming output. 4096 _should_ be enough
set_msg_config -id "Synth 8-5816" -limit 4096
# Now we should be ready to just run synth_design
""",
      synth = f"""
set_param synth.elaboration.rodinMoreOptions "rt::set_parameter var_size_limit 4194304"

update_compile_order -fileset sources_1
update_module_reference design_1_top_0
set_property STEPS.SYNTH_DESIGN.ARGS.RETIMING true [get_runs synth_1]
set_property STEPS.PLACE_DESIGN.TCL.POST {} [get_runs impl_1]
set_property STEPS.PLACE_DESIGN.ARGS.DIRECTIVE ExtraNetDelay_high [get_runs impl_1]
set_property STEPS.PHYS_OPT_DESIGN.ARGS.DIRECTIVE AggressiveExplore [get_runs impl_1]
set_property STEPS.ROUTE_DESIGN.ARGS.DIRECTIVE AggressiveExplore [get_runs impl_1]
set_property STEPS.POST_ROUTE_PHYS_OPT_DESIGN.IS_ENABLED true [get_runs impl_1]
set_property STEPS.POST_ROUTE_PHYS_OPT_DESIGN.ARGS.DIRECTIVE AggressiveExplore [get_runs impl_1]
launch_runs synth_1 -jobs 8
wait_on_run synth_1
""",
      impl = """
launch_runs impl_1 -jobs 8
wait_on_run impl_1

open_run impl_1
report_timing_summary > timing_summary.txt""",
      additional = if (ip_inline) Map() else Map.from(Seq(("ip.tcl", ip_str)))
    )
  }
}
