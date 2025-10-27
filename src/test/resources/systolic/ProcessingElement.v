module ProcessingElement #(parameter DATA_WIDTH_BITS, INT_BITS, FRAC_BITS)(
  input clk,
  // inputs
  input [(DATA_WIDTH_BITS-1):0] wgt,
  input wgt_valid,
  input [(DATA_WIDTH_BITS-1):0] act,
  input act_valid,

  // input coming from the right
  input [(DATA_WIDTH_BITS-1):0] accumulator_shift,
  // ctrl
  input rst_output,
  input shift_out,
  // output (shift out left)
  output reg [(DATA_WIDTH_BITS-1):0] accumulator,
  output reg [(DATA_WIDTH_BITS-1):0] wgt_out,
  output reg wgt_valid_out,
  output reg [(DATA_WIDTH_BITS-1):0] act_out,
  output reg act_valid_out
);
/* We're going to use 16-bit fixed-point sign-magnitude arithmetic, 1b sign, 7b integer, 8b fractional */

// don't feel like subtracting one for verilog [width:0] every time so subtracting one in advance
localparam DATAW_NOSIGN = (DATA_WIDTH_BITS-1)-1;

wire [DATAW_NOSIGN:0] wgt_f = wgt[DATAW_NOSIGN:0];
wire [DATAW_NOSIGN:0] act_f = act[DATAW_NOSIGN:0];
wire                  wgt_s = wgt[DATA_WIDTH_BITS-1];
wire                  act_s = act[DATA_WIDTH_BITS-1];

// take the product of the fractional parts
wire [(2*DATAW_NOSIGN+1):0] product = wgt_f * act_f;
// extract the lower 7b integer bits and the upper 8b of fraction
wire [DATAW_NOSIGN:0] product_f = product[(2*FRAC_BITS+INT_BITS-1) : FRAC_BITS];
// get the new sign bit
wire                  product_s = act_s ^ wgt_s;

// extract the fraction and sign from the current accumulator
wire [DATAW_NOSIGN:0] accumulator_f = accumulator[(DATA_WIDTH_BITS-2):0];
wire                  accumulator_s = accumulator[DATA_WIDTH_BITS-1];

// if the accumulator and product have opposite sign, then we do a subtract from
// the accumulator
wire                  opp_sign = product_s ^ accumulator_s;
wire [DATAW_NOSIGN:0] adj_product_f = opp_sign ? ((~product_f) + 1) : product_f;
wire [DATAW_NOSIGN:0] addition = accumulator_f + adj_product_f;

// if there is an underflow, then we flip the sign of the accumulator
wire                  oflow = addition[DATAW_NOSIGN];
wire                  n_acc_s = accumulator_s ^ oflow;

// if there is an underflow, then it flips to the 2s-complement negative - flip it back to positive
wire [DATAW_NOSIGN:0] n_acc_f = (addition ^ {(DATA_WIDTH_BITS-1){oflow}}) + oflow;
wire [(DATA_WIDTH_BITS-1):0] updated_accumulator = {n_acc_s, n_acc_f};


always @(posedge clk) begin
  if (rst_output) begin
    accumulator <= 0;
    act_valid_out <= 0;
    wgt_valid_out <= 0;
  end else begin
    if (shift_out) begin
      accumulator <= accumulator_shift;
    end else begin
      wgt_valid_out <= wgt_valid;
      act_valid_out <= act_valid;
      wgt_out <= wgt;
      act_out <= act;
      if (wgt_valid && act_valid) begin
        accumulator <= updated_accumulator;
      end
    end
  end
end

endmodule