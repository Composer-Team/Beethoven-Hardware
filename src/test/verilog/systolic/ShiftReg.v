module ShiftReg #(parameter WIDTH = 8, DEPTH=2)(
  input clk,
  input rst,
  input en,
  input [WIDTH-1:0] d_in,
  output [WIDTH-1:0] d_out
);

wire [WIDTH-1:0] full_shreg [0:DEPTH];
reg [WIDTH-1:0] shreg [0:DEPTH-1];
assign full_shreg[0] = d_in;
assign d_out = shreg[DEPTH-1];
integer i;
always @(posedge clk) begin
  if (rst) begin
    for (i = 0; i < DEPTH; i = i + 1) begin
      shreg[i] <= 0;
    end
  end else begin
    if (en) begin
      shreg[0] <= d_in;
      for (i = 1; i < DEPTH; i = i + 1) begin
        shreg[i] <= shreg[i-1];
      end
    end
  end
end

endmodule