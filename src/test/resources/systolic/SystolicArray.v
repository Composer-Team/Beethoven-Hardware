// 8x8 systolic array
module SystolicArray #(parameter SYSTOLIC_ARRAY_DIM, DATA_WIDTH_BITS, INT_BITS, FRAC_BITS) (
  input clk,
  input rst,

  input [(SYSTOLIC_ARRAY_DIM * DATA_WIDTH_BITS - 1):0] act_in,
  input act_valid,
  output act_ready,
  input [(SYSTOLIC_ARRAY_DIM * DATA_WIDTH_BITS - 1):0] wgt_in,
  input wgt_valid,
  output wgt_ready,

  output [(SYSTOLIC_ARRAY_DIM * DATA_WIDTH_BITS - 1):0] accumulator_out,
  output accumulator_out_valid,
  input accumulator_out_ready,

  input ctrl_start_matmul,
  output ctrl_start_ready,
  input [19:0] ctrl_inner_dimension
);

// we're going to split up the 128b bus into 16b payloads
wire [(DATA_WIDTH_BITS - 1):0] wgt_shifts   [0:SYSTOLIC_ARRAY_DIM]     [0:(SYSTOLIC_ARRAY_DIM-1)];
wire [(DATA_WIDTH_BITS - 1):0] act_shifts   [0:(SYSTOLIC_ARRAY_DIM-1)] [0:SYSTOLIC_ARRAY_DIM];
wire [(DATA_WIDTH_BITS - 1):0] out_shifts   [0:(SYSTOLIC_ARRAY_DIM-1)] [0:SYSTOLIC_ARRAY_DIM];
// valid bits
wire                           wgt_v_shifts [0:SYSTOLIC_ARRAY_DIM]     [0:(SYSTOLIC_ARRAY_DIM-1)];
wire                           act_v_shifts [0:(SYSTOLIC_ARRAY_DIM-1)] [0:SYSTOLIC_ARRAY_DIM];


// state machine
`define IDLE 0
`define GO 1
`define DRAIN 2
`define SHIFT 3
reg [1:0] state;

wire shift_out = state == `SHIFT;
assign accumulator_out_valid = state == `SHIFT;
assign ctrl_start_ready = state == `IDLE;

// since weights and activations are ONLY accepted simultaneously
// to produce the dot product, only consume an input when both activations
// and weights are valid
// There's no need for back pressure because it's output stationary
assign wgt_ready = act_valid && wgt_valid;
assign act_ready = act_valid && wgt_valid;

// count down 
reg [19:0] inner_dimension_ctr;
wire [19:0] n_inner_dimension_ctr = inner_dimension_ctr - 1;
wire can_increment_inputs = (act_valid && wgt_valid) || state == `DRAIN;

always @(posedge clk) begin
  if (rst) begin
    state <= `IDLE;
  end else begin
    if (state == `IDLE) begin
      if (ctrl_start_matmul) begin
        inner_dimension_ctr <= ctrl_inner_dimension;
        state <= `GO;
      end
    end else if (state == `GO) begin
      if (can_increment_inputs) begin
        if (n_inner_dimension_ctr == 0) begin
          inner_dimension_ctr <= (2 * SYSTOLIC_ARRAY_DIM);
          state <= `DRAIN;
        end else begin
          inner_dimension_ctr <= n_inner_dimension_ctr;
        end
      end
    end else if (state == `DRAIN) begin
      inner_dimension_ctr <= n_inner_dimension_ctr;
      if (n_inner_dimension_ctr == 0) begin
        inner_dimension_ctr <= (SYSTOLIC_ARRAY_DIM);
        state <= `SHIFT;
      end
    end else begin
      if (accumulator_out_ready) begin
        inner_dimension_ctr <= n_inner_dimension_ctr;
        if (n_inner_dimension_ctr == 0) begin
          state <= `IDLE;
        end
      end
    end
  end
end

generate
  genvar i, j;
  for (i = 0; i < SYSTOLIC_ARRAY_DIM; i = i + 1) begin
    wire [(DATA_WIDTH_BITS - 1):0] wgt_slice;
    assign wgt_slice = wgt_in[((i+1) * DATA_WIDTH_BITS)-1 -: DATA_WIDTH_BITS];
    assign accumulator_out[(i+1)*DATA_WIDTH_BITS - 1 -: DATA_WIDTH_BITS] = out_shifts[i][0];
    if (i == 0) begin
      assign wgt_shifts[0][i] = wgt_slice;
      assign wgt_v_shifts[0][i] = wgt_valid;
    end else begin
      ShiftReg #(.WIDTH(DATA_WIDTH_BITS + 1), .DEPTH(i)) sr  (
        .clk(clk),
        .rst(rst),
        .en(can_increment_inputs),
        .d_in({wgt_valid, wgt_slice}),
        .d_out({wgt_v_shifts[0][i], wgt_shifts[0][i]})
      );
    end

    wire [(DATA_WIDTH_BITS - 1):0] act_slice = act_in[((i+1) * DATA_WIDTH_BITS)-1 -: DATA_WIDTH_BITS];
    if (i == 0) begin
      assign act_shifts[i][0] = act_slice;
      assign act_v_shifts[i][0] = wgt_valid;
    end else begin
      wire [DATA_WIDTH_BITS:0] sr_out;
      ShiftReg #(.WIDTH(DATA_WIDTH_BITS + 1), .DEPTH(i)) sr  (
        .clk(clk),
        .rst(rst),
        .en(can_increment_inputs),
        .d_in({act_valid, act_slice}),
        .d_out({act_v_shifts[i][0], act_shifts[i][0]})
      );
    end
    for (j = 0; j < SYSTOLIC_ARRAY_DIM; j = j + 1) begin
      ProcessingElement #(.DATA_WIDTH_BITS(DATA_WIDTH_BITS), .FRAC_BITS(FRAC_BITS), .INT_BITS(INT_BITS)) pe(
        .clk(clk),

        .wgt(wgt_shifts[i][j]),
        .wgt_valid(wgt_v_shifts[i][j]),

        .act(act_shifts[i][j]),
        .act_valid(act_v_shifts[i][j]),

        .accumulator(out_shifts[i][j]),
        .accumulator_shift(out_shifts[i][j+1]),
        .shift_out(shift_out && accumulator_out_ready),

        .rst_output(ctrl_start_matmul),

        .wgt_out(wgt_shifts[i+1][j]),
        .wgt_valid_out(wgt_v_shifts[i+1][j]),
        .act_out(act_shifts[i][j+1]),
        .act_valid_out(act_v_shifts[i][j+1])
      );
    end
  end
endgenerate

endmodule