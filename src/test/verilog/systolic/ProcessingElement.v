module ProcessingElement (
  input clk,
  // inputs
  input [15:0] wgt,
  input wgt_valid,
  input [15:0] act,
  input act_valid,

  input [15:0] accumulator_shift,
  // ctrl
  input rst_output,
  input shift_out,
  // output (shift out left)
  output reg [15:0] accumulator,
  output reg [15:0] wgt_out,
  output reg wgt_valid_out,
  output reg [15:0] act_out,
  output reg act_valid_out
);
/* We're going to use 16-bit fixed-point sign-magnitude arithmetic, 1b sign, 7b integer, 8b fractional */

wire [14:0] wgt_f = wgt[14:0];
wire [14:0] act_f = act[14:0];
wire        wgt_s = wgt[15];
wire        act_s = act[15];

// take the product of the fractional parts
wire [29:0] product = wgt_f * act_f ;
// extract the lower 7b integer bits and the upper 8b of fraction
wire [14:0] product_f = product[23:8];
// get the new sign bit
wire        product_s = act_s ^ wgt_s;

// extract the fraction and sign from the current accumulator
wire [14:0] accumulator_f = accumulator[14:0];
wire        accumulator_s = accumulator[15];

// if the accumulator and product have opposite sign, then we do a subtract from
// the accumulator
wire        opp_sign = product_s ^ accumulator_s;
wire [14:0] adj_product_f = opp_sign ? ((~product_f) + 1) : product_f;
wire [14:0] addition = accumulator_f + adj_product_f;

// if there is an underflow, then we flip the sign of the accumulator
wire        oflow = addition[14];
wire        n_acc_s = accumulator_s ^ oflow;

// if there is an underflow, then it flips to the 2s-complement negative - flip it back to positive
wire [14:0] n_acc_f = (addition ^ {16{oflow}}) + oflow;
wire [15:0] updated_accumulator = {n_acc_s, n_acc_f};

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
      if (wgt_valid && act_valid) begin
        accumulator <= updated_accumulator;
      end
    end
  end
  wgt_out <= wgt;
  act_out <= act;
end

endmodule