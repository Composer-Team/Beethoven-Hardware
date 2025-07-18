package beethoven.Platforms.FPGA.Xilinx.AWS

import os.Path

object SynthScript {
  def write(to_path: Path): Unit = {
    os.write.over(to_path,
      f"""
         |source $${HDK_SHELL_DIR}/build/scripts/synth_cl_header.tcl
         |
         |
         |###############################################################################
         |print "Reading encrypted user source codes"
         |###############################################################################
         |
         |#---- User would replace this section -----
         |
         |# Reading the .sv and .v files, as proper designs would not require reading
         |# .vh, nor .inc files
         |read_verilog -sv [glob ~/cl_beethoven_top/design/*v]
         |#set_property file_type {Verilog Header} [get_files ~/cl_beethoven_top/design/cl_beethoven_top_defines.vh]
         |#set_property is_global_include true     [get_files ~/cl_beethoven_top/design/cl_beethoven_top_defines.vh]
         |
         |#---- End of section replaced by User ----
         |
         |###############################################################################
         |print "Reading CL IP blocks"
         |###############################################################################
         |
         |## DDR IP
         |read_ip [ list \\
         |  $${HDK_IP_SRC_DIR}/cl_ddr4_32g/cl_ddr4_32g.xci \\
         |  $${HDK_IP_SRC_DIR}/cl_ddr4_32g_ap/cl_ddr4_32g_ap.xci \\
         |  $${HDK_IP_SRC_DIR}/cl_ddr4/cl_ddr4.xci \\
         |  $${HDK_IP_SRC_DIR}/cl_ddr4_64g_ap/cl_ddr4_64g_ap.xci
         |]
         |
         |## HBM IP's
         |#read_ip [ list \\
         |#  $${HDK_IP_SRC_DIR}/cl_hbm/cl_hbm.xci
         |#]
         |
         |## Clocking IP's
         |read_ip [ list \\
         |  $$HDK_SHELL_DESIGN_DIR/../../ip/cl_ip/cl_ip.srcs/sources_1/ip/clk_mmcm_a/clk_mmcm_a.xci \\
         |  $$HDK_SHELL_DESIGN_DIR/../../ip/cl_ip/cl_ip.srcs/sources_1/ip/clk_mmcm_b/clk_mmcm_b.xci \\
         |  $$HDK_SHELL_DESIGN_DIR/../../ip/cl_ip/cl_ip.srcs/sources_1/ip/clk_mmcm_c/clk_mmcm_c.xci \\
         |  $$HDK_SHELL_DESIGN_DIR/../../ip/cl_ip/cl_ip.srcs/sources_1/ip/clk_mmcm_hbm/clk_mmcm_hbm.xci \\
         |  $$HDK_SHELL_DESIGN_DIR/../../ip/cl_ip/cl_ip.srcs/sources_1/ip/cl_clk_axil_xbar/cl_clk_axil_xbar.xci \\
         |  $$HDK_SHELL_DESIGN_DIR/../../ip/cl_ip/cl_ip.srcs/sources_1/ip/cl_sda_axil_xbar/cl_sda_axil_xbar.xci
         |]
         |
         |## AXI Register Slice IP's
         |read_ip [ list \\
         |  $${HDK_IP_SRC_DIR}/axi_register_slice/axi_register_slice.xci \\
         |  $${HDK_IP_SRC_DIR}/axi_register_slice_light/axi_register_slice_light.xci \\
         |  $${HDK_IP_SRC_DIR}/cl_axi_register_slice_light/cl_axi_register_slice_light.xci \\
         |  $${HDK_IP_SRC_DIR}/cl_axi3_256b_reg_slice/cl_axi3_256b_reg_slice.xci
         |]
         |
         |## AXI Conversion IP's
         |read_ip [ list \\
         |  $${HDK_IP_SRC_DIR}/cl_axi_clock_converter/cl_axi_clock_converter.xci \\
         |  $${HDK_IP_SRC_DIR}/cl_axi_clock_converter_light/cl_axi_clock_converter_light.xci
         |]
         |
         |## AXI Utility IP's
         |read_ip [ list \\
         |  $${HDK_IP_SRC_DIR}/cl_axi_interconnect/cl_axi_interconnect.xci \\
         |  $${HDK_IP_SRC_DIR}/cl_axi_interconnect_64G_ddr/cl_axi_interconnect_64G_ddr.xci
         |]
         |
         |## Read IP for virtual jtag / ILA/VIO
         |read_ip [ list \\
         |  $${HDK_IP_SRC_DIR}/cl_debug_bridge/cl_debug_bridge.xci \\
         |  $${HDK_IP_SRC_DIR}/ila_1/ila_1.xci \\
         |  $${HDK_IP_SRC_DIR}/ila_vio_counter/ila_vio_counter.xci \\
         |  $${HDK_IP_SRC_DIR}/vio_0/vio_0.xci
         |]
         |
         |## Read BD IPs
         |add_files [ list \\
         |  $${HDK_BD_SRC_DIR}/cl_axi_sc_1x1/cl_axi_sc_1x1.bd \\
         |  $${HDK_BD_SRC_DIR}/cl_axi_sc_2x2/cl_axi_sc_2x2.bd
         |]
         |
         |read_verilog [ list \\
         |  $${HDK_BD_GEN_DIR}/cl_axi_sc_1x1/hdl/cl_axi_sc_1x1_wrapper.v \\
         |  $${HDK_BD_GEN_DIR}/cl_axi_sc_2x2/hdl/cl_axi_sc_2x2_wrapper.v
         |]
         |
         |#---- End of section uncommented by the User ----
         |
         |###############################################################################
         |print "Reading user constraints"
         |###############################################################################
         |
         |#---- User would replace this section -----
         |
         |read_xdc [ list \\
         |  $${constraints_dir}/cl_synth_user.xdc \\
         |  $${constraints_dir}/cl_timing_user.xdc \\
            user_constraints.xdc \\
         |]
         |
         |set_property PROCESSING_ORDER LATE [get_files cl_synth_user.xdc]
         |set_property PROCESSING_ORDER LATE [get_files cl_timing_user.xdc]
         |
         |#---- End of section replaced by User ----
         |
         |###############################################################################
         |print "Starting synthesizing customer design $${CL}"
         |###############################################################################
         |update_compile_order -fileset sources_1
         |
         |synth_design -mode out_of_context \\
         |             -top $${CL} \\
         |             -verilog_define XSDB_SLV_DIS \\
         |             -part $${DEVICE_TYPE} \\
         |             -keep_equivalent_registers
         |
         |###############################################################################
         |print "Connecting debug network"
         |###############################################################################
         |
         |#---- User would replace this section -----
         |
         |# Connect debug network
         |set cl_ila_cells [get_cells [list CL_ILA/CL_DMA_ILA_0 CL_ILA/ddr_A_hookup.CL_DDRA_ILA_0]]
         |if {$$cl_ila_cells != ""} {
         |  connect_debug_cores -master [get_cells [get_debug_cores -filter {NAME=~*CL_DEBUG_BRIDGE*}]] \\
         |                      -slaves $$cl_ila_cells
         |}
         |
         |#---- End of section replaced by User ----
         |
         |
         |
         |# Common footer
         |source $${HDK_SHELL_DIR}/build/scripts/synth_cl_footer.tcl
         |""".stripMargin)
  }
}
