
module SystolicArrayCore (
  input clock,
  input reset,

  input          cmd_0_valid,
  output         cmd_0_ready,
  input  [63:0]  cmd_0_act_addr,
  input  [19:0]  cmd_0_inner_dimension,
  input  [63:0]  cmd_0_out_addr,
  input  [63:0]  cmd_0_wgt_addr,
  output         resp_0_valid,
  input          resp_0_ready,
  output         weights_req_valid,
  input          weights_req_ready,
  output [33:0]  weights_req_len,
  output [63:0]  weights_req_addr_address,
  output         activations_req_valid,
  input          activations_req_ready,
  output [33:0]  activations_req_len,
  output [63:0]  activations_req_addr_address,
  input          weights_data_valid,
  output         weights_data_ready,
  input          weights_inProgress,
  input          activations_inProgress,
  input  [127:0] weights_data,
  input          activations_data_valid,
  output         activations_data_ready,
  input  [127:0] activations_data,
  output         vec_out_req_valid,
  input          vec_out_req_ready,
  output [33:0]  vec_out_req_len,
  output [63:0]  vec_out_req_addr_address,
  output         vec_out_data_valid,
  input          vec_out_data_ready,
  output [127:0] vec_out_data
);

wire cmd_fire = cmd_0_valid && cmd_0_ready;

assign vec_out_req_valid = cmd_fire;
assign vec_out_req_len = 2 * 8 * 8;
assign vec_out_req_addr_address = cmd_0_out_addr;

assign weights_req_valid = cmd_fire;
assign weights_req_len = 2 * 8 * cmd_0_inner_dimension;
assign weights_req_addr_address = cmd_0_wgt_addr;

assign activations_req_valid = cmd_fire;
assign activations_req_len = 2 * 8 * cmd_0_inner_dimension;
assign activations_req_addr_address = cmd_0_act_addr;

`define IDLE 0
`define GO 1
`define FLUSH 2
`define RESPONSE 3
reg [1:0] state;
assign cmd_0_ready = state == `IDLE 
        && weights_req_ready 
        && activations_req_ready 
        && vec_out_req_ready;
assign resp_0_valid = state == `RESPONSE;

wire sa_idle;

SystolicArray sa(
  .clk(clock),
  .rst(reset),

  .act_in(activations_data),
  .act_valid(activations_data_valid),
  .act_ready(activations_data_ready),

  .wgt_in(weights_data),
  .wgt_valid(weights_data_valid),
  .wgt_ready(weights_data_ready),

  .accumulator_out(vec_out_data),
  .accumulator_out_valid(vec_out_data_valid),
  .accumulator_out_ready(vec_out_data_ready),

  .ctrl_start_matmul(cmd_fire),
  .ctrl_start_ready(sa_idle),
  .ctrl_inner_dimension(cmd_0_inner_dimension)
);

always @(posedge clock) begin
  if (reset) begin
    state <= `IDLE;
  end else begin
    if (state == `IDLE) begin
      if (cmd_fire) begin
        state <= `GO;
      end
    end else if (state == `GO) begin
      if (sa_idle) begin
        state <= `FLUSH;
      end
    end else if (state == `FLUSH) begin
      if (vec_out_req_ready) begin
        state <= `RESPONSE;
      end
    end else if (state == `RESPONSE) begin
      if (resp_0_ready) begin
        state <= `IDLE;
      end
    end
  end
end

endmodule
