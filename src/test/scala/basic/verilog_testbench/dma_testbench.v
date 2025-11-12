`timescale 1ns/1ps

module BeethovenTopVCSHarness;
reg [15:0] S00_AXI_awid;
reg [39:0] S00_AXI_awaddr;
reg [7:0] S00_AXI_awlen;
reg [2:0] S00_AXI_awsize;
reg [1:0] S00_AXI_awburst;
reg [0:0] S00_AXI_awlock;
reg [3:0] S00_AXI_awcache;
reg [2:0] S00_AXI_awprot;
reg [3:0] S00_AXI_awregion;
reg [3:0] S00_AXI_awqos;
reg [0:0] S00_AXI_awvalid;
reg [63:0] S00_AXI_wdata;
reg [7:0] S00_AXI_wstrb;
reg [0:0] S00_AXI_wlast;
reg [0:0] S00_AXI_wvalid;
reg [0:0] S00_AXI_bready;
reg [15:0] S00_AXI_arid;
reg [39:0] S00_AXI_araddr;
reg [7:0] S00_AXI_arlen;
reg [2:0] S00_AXI_arsize;
reg [1:0] S00_AXI_arburst;
reg [0:0] S00_AXI_arlock;
reg [3:0] S00_AXI_arcache;
reg [2:0] S00_AXI_arprot;
reg [3:0] S00_AXI_arregion;
reg [3:0] S00_AXI_arqos;
reg [0:0] S00_AXI_arvalid;
reg [0:0] S00_AXI_rready;
reg [48:0] dma_awaddr;
reg [7:0] dma_awlen;
reg [2:0] dma_awsize;
reg [1:0] dma_awburst;
reg [0:0] dma_awlock;
reg [3:0] dma_awcache;
reg [2:0] dma_awprot;
reg [3:0] dma_awregion;
reg [3:0] dma_awqos;
reg [0:0] dma_awvalid;
reg [31:0] dma_wdata;
reg [3:0] dma_wstrb;
reg [0:0] dma_wlast;
reg [0:0] dma_wvalid;
reg [0:0] dma_bready;
reg [48:0] dma_araddr;
reg [7:0] dma_arlen;
reg [2:0] dma_arsize;
reg [1:0] dma_arburst;
reg [0:0] dma_arlock;
reg [3:0] dma_arcache;
reg [2:0] dma_arprot;
reg [3:0] dma_arregion;
reg [3:0] dma_arqos;
reg [0:0] dma_arvalid;
reg [0:0] dma_rready;
reg [0:0] M00_AXI_awready;
reg [0:0] M00_AXI_wready;
reg [5:0] M00_AXI_bid;
reg [1:0] M00_AXI_bresp;
reg [0:0] M00_AXI_bvalid;
reg [0:0] M00_AXI_arready;
reg [5:0] M00_AXI_rid;
reg [127:0] M00_AXI_rdata;
reg [1:0] M00_AXI_rresp;
reg [0:0] M00_AXI_rlast;
reg [0:0] M00_AXI_rvalid;

wire [0:0] S00_AXI_awready;
wire [0:0] S00_AXI_wready;
wire [15:0] S00_AXI_bid;
wire [1:0] S00_AXI_bresp;
wire [0:0] S00_AXI_bvalid;
wire [0:0] S00_AXI_arready;
wire [15:0] S00_AXI_rid;
wire [63:0] S00_AXI_rdata;
wire [1:0] S00_AXI_rresp;
wire [0:0] S00_AXI_rlast;
wire [0:0] S00_AXI_rvalid;
wire [0:0] dma_awready;
wire [0:0] dma_wready;
wire [1:0] dma_bresp;
wire [0:0] dma_bvalid;
wire [0:0] dma_arready;
wire [31:0] dma_rdata;
wire [1:0] dma_rresp;
wire [0:0] dma_rlast;
wire [0:0] dma_rvalid;
wire [5:0] M00_AXI_awid;
wire [48:0] M00_AXI_awaddr;
wire [7:0] M00_AXI_awlen;
wire [2:0] M00_AXI_awsize;
wire [1:0] M00_AXI_awburst;
wire [0:0] M00_AXI_awlock;
wire [3:0] M00_AXI_awcache;
wire [2:0] M00_AXI_awprot;
wire [3:0] M00_AXI_awregion;
wire [3:0] M00_AXI_awqos;
wire [0:0] M00_AXI_awvalid;
wire [127:0] M00_AXI_wdata;
wire [15:0] M00_AXI_wstrb;
wire [0:0] M00_AXI_wlast;
wire [0:0] M00_AXI_wvalid;
wire [0:0] M00_AXI_bready;
wire [5:0] M00_AXI_arid;
wire [48:0] M00_AXI_araddr;
wire [7:0] M00_AXI_arlen;
wire [2:0] M00_AXI_arsize;
wire [1:0] M00_AXI_arburst;
wire [0:0] M00_AXI_arlock;
wire [3:0] M00_AXI_arcache;
wire [2:0] M00_AXI_arprot;
wire [3:0] M00_AXI_arregion;
wire [3:0] M00_AXI_arqos;
wire [0:0] M00_AXI_arvalid;
wire [0:0] M00_AXI_rready;

reg clock = 0;
reg ARESETn = 0;
BeethovenTop top(
  .clock(clock),
  .ARESETn(ARESETn),
  .S00_AXI_awid(S00_AXI_awid),
  .S00_AXI_awaddr(S00_AXI_awaddr),
  .S00_AXI_awlen(S00_AXI_awlen),
  .S00_AXI_awsize(S00_AXI_awsize),
  .S00_AXI_awburst(S00_AXI_awburst),
  .S00_AXI_awlock(S00_AXI_awlock),
  .S00_AXI_awcache(S00_AXI_awcache),
  .S00_AXI_awprot(S00_AXI_awprot),
  .S00_AXI_awregion(S00_AXI_awregion),
  .S00_AXI_awqos(S00_AXI_awqos),
  .S00_AXI_awvalid(S00_AXI_awvalid),
  .S00_AXI_wdata(S00_AXI_wdata),
  .S00_AXI_wstrb(S00_AXI_wstrb),
  .S00_AXI_wlast(S00_AXI_wlast),
  .S00_AXI_wvalid(S00_AXI_wvalid),
  .S00_AXI_bready(S00_AXI_bready),
  .S00_AXI_arid(S00_AXI_arid),
  .S00_AXI_araddr(S00_AXI_araddr),
  .S00_AXI_arlen(S00_AXI_arlen),
  .S00_AXI_arsize(S00_AXI_arsize),
  .S00_AXI_arburst(S00_AXI_arburst),
  .S00_AXI_arlock(S00_AXI_arlock),
  .S00_AXI_arcache(S00_AXI_arcache),
  .S00_AXI_arprot(S00_AXI_arprot),
  .S00_AXI_arregion(S00_AXI_arregion),
  .S00_AXI_arqos(S00_AXI_arqos),
  .S00_AXI_arvalid(S00_AXI_arvalid),
  .S00_AXI_rready(S00_AXI_rready),
  .dma_awaddr(dma_awaddr),
  .dma_awlen(dma_awlen),
  .dma_awsize(dma_awsize),
  .dma_awburst(dma_awburst),
  .dma_awlock(dma_awlock),
  .dma_awcache(dma_awcache),
  .dma_awprot(dma_awprot),
  .dma_awregion(dma_awregion),
  .dma_awqos(dma_awqos),
  .dma_awvalid(dma_awvalid),
  .dma_wdata(dma_wdata),
  .dma_wstrb(dma_wstrb),
  .dma_wlast(dma_wlast),
  .dma_wvalid(dma_wvalid),
  .dma_bready(dma_bready),
  .dma_araddr(dma_araddr),
  .dma_arlen(dma_arlen),
  .dma_arsize(dma_arsize),
  .dma_arburst(dma_arburst),
  .dma_arlock(dma_arlock),
  .dma_arcache(dma_arcache),
  .dma_arprot(dma_arprot),
  .dma_arregion(dma_arregion),
  .dma_arqos(dma_arqos),
  .dma_arvalid(dma_arvalid),
  .dma_rready(dma_rready),
  .M00_AXI_awready(M00_AXI_awready),
  .M00_AXI_wready(M00_AXI_wready),
  .M00_AXI_bid(M00_AXI_bid),
  .M00_AXI_bresp(M00_AXI_bresp),
  .M00_AXI_bvalid(M00_AXI_bvalid),
  .M00_AXI_arready(M00_AXI_arready),
  .M00_AXI_rid(M00_AXI_rid),
  .M00_AXI_rdata(M00_AXI_rdata),
  .M00_AXI_rresp(M00_AXI_rresp),
  .M00_AXI_rlast(M00_AXI_rlast),
  .M00_AXI_rvalid(M00_AXI_rvalid),
  .S00_AXI_awready(S00_AXI_awready),
  .S00_AXI_wready(S00_AXI_wready),
  .S00_AXI_bid(S00_AXI_bid),
  .S00_AXI_bresp(S00_AXI_bresp),
  .S00_AXI_bvalid(S00_AXI_bvalid),
  .S00_AXI_arready(S00_AXI_arready),
  .S00_AXI_rid(S00_AXI_rid),
  .S00_AXI_rdata(S00_AXI_rdata),
  .S00_AXI_rresp(S00_AXI_rresp),
  .S00_AXI_rlast(S00_AXI_rlast),
  .S00_AXI_rvalid(S00_AXI_rvalid),
  .dma_awready(dma_awready),
  .dma_wready(dma_wready),
  .dma_bresp(dma_bresp),
  .dma_bvalid(dma_bvalid),
  .dma_arready(dma_arready),
  .dma_rdata(dma_rdata),
  .dma_rresp(dma_rresp),
  .dma_rlast(dma_rlast),
  .dma_rvalid(dma_rvalid),
  .M00_AXI_awid(M00_AXI_awid),
  .M00_AXI_awaddr(M00_AXI_awaddr),
  .M00_AXI_awlen(M00_AXI_awlen),
  .M00_AXI_awsize(M00_AXI_awsize),
  .M00_AXI_awburst(M00_AXI_awburst),
  .M00_AXI_awlock(M00_AXI_awlock),
  .M00_AXI_awcache(M00_AXI_awcache),
  .M00_AXI_awprot(M00_AXI_awprot),
  .M00_AXI_awregion(M00_AXI_awregion),
  .M00_AXI_awqos(M00_AXI_awqos),
  .M00_AXI_awvalid(M00_AXI_awvalid),
  .M00_AXI_wdata(M00_AXI_wdata),
  .M00_AXI_wstrb(M00_AXI_wstrb),
  .M00_AXI_wlast(M00_AXI_wlast),
  .M00_AXI_wvalid(M00_AXI_wvalid),
  .M00_AXI_bready(M00_AXI_bready),
  .M00_AXI_arid(M00_AXI_arid),
  .M00_AXI_araddr(M00_AXI_araddr),
  .M00_AXI_arlen(M00_AXI_arlen),
  .M00_AXI_arsize(M00_AXI_arsize),
  .M00_AXI_arburst(M00_AXI_arburst),
  .M00_AXI_arlock(M00_AXI_arlock),
  .M00_AXI_arcache(M00_AXI_arcache),
  .M00_AXI_arprot(M00_AXI_arprot),
  .M00_AXI_arregion(M00_AXI_arregion),
  .M00_AXI_arqos(M00_AXI_arqos),
  .M00_AXI_arvalid(M00_AXI_arvalid),
  .M00_AXI_rready(M00_AXI_rready)
);

reg dump_reg = 1'b0;

initial begin:a2
  `ifndef ICARUS
    $vcdplusfile("BeethovenTrace.vpd");
  `endif
  $dumpvars(0, top);
  `ifndef ICARUS
    $vcdpluson;
  `endif
  $init_input_signals(clock, ARESETn, S00_AXI_awid, S00_AXI_awaddr, S00_AXI_awlen, S00_AXI_awsize, S00_AXI_awburst, S00_AXI_awlock, S00_AXI_awcache, S00_AXI_awprot, S00_AXI_awregion, S00_AXI_awqos, S00_AXI_awvalid, S00_AXI_wdata, S00_AXI_wstrb, S00_AXI_wlast, S00_AXI_wvalid, S00_AXI_bready, S00_AXI_arid, S00_AXI_araddr, S00_AXI_arlen, S00_AXI_arsize, S00_AXI_arburst, S00_AXI_arlock, S00_AXI_arcache, S00_AXI_arprot, S00_AXI_arregion, S00_AXI_arqos, S00_AXI_arvalid, S00_AXI_rready, dma_awaddr, dma_awlen, dma_awsize, dma_awburst, dma_awlock, dma_awcache, dma_awprot, dma_awregion, dma_awqos, dma_awvalid, dma_wdata, dma_wstrb, dma_wlast, dma_wvalid, dma_bready, dma_araddr, dma_arlen, dma_arsize, dma_arburst, dma_arlock, dma_arcache, dma_arprot, dma_arregion, dma_arqos, dma_arvalid, dma_rready, M00_AXI_awready, M00_AXI_wready, M00_AXI_bid, M00_AXI_bresp, M00_AXI_bvalid, M00_AXI_arready, M00_AXI_rid, M00_AXI_rdata, M00_AXI_rresp, M00_AXI_rlast, M00_AXI_rvalid);
  $init_output_signals(S00_AXI_awready, S00_AXI_wready, S00_AXI_bid, S00_AXI_bresp, S00_AXI_bvalid, S00_AXI_arready, S00_AXI_rid, S00_AXI_rdata, S00_AXI_rresp, S00_AXI_rlast, S00_AXI_rvalid, dma_awready, dma_wready, dma_bresp, dma_bvalid, dma_arready, dma_rdata, dma_rresp, dma_rlast, dma_rvalid, M00_AXI_awid, M00_AXI_awaddr, M00_AXI_awlen, M00_AXI_awsize, M00_AXI_awburst, M00_AXI_awlock, M00_AXI_awcache, M00_AXI_awprot, M00_AXI_awregion, M00_AXI_awqos, M00_AXI_awvalid, M00_AXI_wdata, M00_AXI_wstrb, M00_AXI_wlast, M00_AXI_wvalid, M00_AXI_bready, M00_AXI_arid, M00_AXI_araddr, M00_AXI_arlen, M00_AXI_arsize, M00_AXI_arburst, M00_AXI_arlock, M00_AXI_arcache, M00_AXI_arprot, M00_AXI_arregion, M00_AXI_arqos, M00_AXI_arvalid, M00_AXI_rready);
  $init_structures;
end

initial begin:z
  $dumpon;
end

always @(clock) begin
  $dumpflush;
end
task init_everything_low;
  begin
    M00_AXI_arready = 0;
    M00_AXI_awready = 0;
    M00_AXI_wready = 0;
    M00_AXI_rvalid = 0;
    M00_AXI_bvalid = 0;
    dma_awvalid = 0;
    dma_arvalid = 0;
    dma_wvalid = 0;
    dma_rready = 0;
    dma_bready = 0;
  end
endtask

task enqueue_write;
  input [31:0] addr;
  input [31:0] data;
  begin
    dma_arvalid = 0;
    dma_awvalid = 0;
    dma_wvalid = 0;
    dma_bready = 0;
    M00_AXI_awready = 0;
    M00_AXI_wready = 0;
    for (i=0;i<100;i=i+1) begin
      # 1.0;
      clock = ~clock;
    end
    #1
    clock = 1;
    M00_AXI_bvalid = 0;
    M00_AXI_bid = 0;

    dma_arvalid = 0;
    dma_rready = 0;

    dma_awvalid = 1;
    dma_awaddr = addr;

    dma_wvalid = 1;
    dma_wdata = data;
    dma_wstrb = 4'hF;
    dma_bready = 1;
    #1
    clock = 0;
    #1
    clock = 1;
    dma_awvalid = 0;
    dma_wvalid = 0;
    #1
    clock = 0;
  end
endtask

integer j;
task sink_write;
  begin
    reg [15:0] id;
    #1
    clock = 1;
    #1
    clock = 0;
    #1
    clock = 1;
    M00_AXI_awready = 1;
    M00_AXI_wready = 1;
    while (!M00_AXI_awready) begin
      #1
      clock = 0;
      #1
      clock = 1;
    end
    id = M00_AXI_awid;
    $display("FIRE: %d, DATA: %x, ID: %d", M00_AXI_wready && M00_AXI_wvalid, M00_AXI_wdata, M00_AXI_awid);
    #1
    clock = 0;
    #1
    clock = 1;
    M00_AXI_awready = 0;
    M00_AXI_wready = 0;
    for (i=0;i<100;i=i+1) begin
      #1
      clock = ~clock;
    end
    #1
    clock = 1;
    M00_AXI_bvalid = 1;
    M00_AXI_bid = id;
    while (!M00_AXI_bready) begin
      #1
      clock = 0;
      #1
      clock = 1;
    end
    #1
    clock = 0;
    #1
    clock = 1;
    M00_AXI_bvalid = 0;
    $display("Successfully sunk write response");
  end
endtask
task write;
  input [31:0] addr;
  input [31:0] data;
  begin
    enqueue_write(addr, data);
    sink_write();
  end
endtask

task enqueue_read;
  input [31:0] addr;
  begin
    #1
    clock = 0;
    #1
    clock = 1;
    dma_arvalid = 0;
    dma_rready = 0;
    for (i=0;i<100;i=i+1) begin
      #1
      clock = 0;
      #1
      clock = 1;
    end
    dma_arvalid = 1;
    dma_araddr = addr;
    dma_rready = 0;
    #1
    clock = 0;
    #1
    clock = 1;
    dma_arvalid = 0;
    M00_AXI_rvalid = 0;
    #1
    clock = 0;
    #1.0
    for (i=0;i<100;i=i+1) begin
      #1
      clock = ~clock;
    end
  end
endtask

task process_read;
  output [15:0] id;
  begin
    reg [15:0] id_reg;
    #1
    clock = 0;
    #1
    clock = 1;
    M00_AXI_arready = 1;
    while (!M00_AXI_arvalid) begin
      #1
      clock = 0;
      #1
      clock = 1;
    end
    id = M00_AXI_arid;
    #1
    clock = 0;
    M00_AXI_arready = 0;
    #1
    clock = 1;
  end
endtask
task sink_read;
  input [127:0] data;
  input [15:0] id;
  begin
    #1
    clock = 0;
    #1
    clock = 1;
    M00_AXI_rvalid = 1;
    M00_AXI_rdata = data;
    M00_AXI_rid = id;
    while(!M00_AXI_rready) begin
      #1
      clock = 0;
      #1
      clock = 1;
    end
    $display("submitted read");
    #1
    clock = 0;
    #1
    clock = 1;
    M00_AXI_rvalid = 0;
    dma_rready = 1;
    while(!dma_rvalid) begin
      #1
      clock = 0;
      #1
      clock = 1;
    end
    $display("Got read: %x", dma_rdata);
    #1
    clock = 0;
    #1
    clock = 1;
    dma_rready = 0;
  end
endtask

task read;
  input [31:0] addr;
  begin
    reg [15:0] id;
    enqueue_read(addr);
    process_read(id);
    sink_read(128'hAAAABBBBCCCCDDDD0000111122223333, id);
  end
endtask


task query_credits;
  begin
    enqueue_read(0);
    #1
    clock = 0;
    clock = 1;
    dma_rready = 1;
    while (!dma_rvalid) begin
      #1
      clock = 0;
      #1
      clock = 1;
    end
    $display("Credits Available: %d", dma_rdata);
    #1
    clock = 0;
    dma_rready = 0;
    #1
    clock = 1;
  end
endtask

integer i;
integer k;
initial begin:a1
  // areset logic BEGIN
  ARESETn = 0;
  init_everything_low();
  for (i=0;i<100;i=i+1) begin
    # 1.0;
    clock = ~clock;
  end
  ARESETn = 1;
  for (i=0;i<100;i=i+1) begin
    #1
    clock = ~clock;
  end
  #1
  // areset logic END
  for (k = 0; k < 60; k = k + 1) begin
    enqueue_write(16'hDE00, 32'hDEADBEEF);
  end

  query_credits();
  
  // query for the number of queries, but don't read anything real

  for (k = 0; k < 60; k = k + 1) begin
    sink_write();
  end
  for (i=0;i<100;i=i+1) begin
    #1
    clock = ~clock;
  end
  query_credits();
  $display("Should expect 63 credits above");

  read(16'h20); 
  read(16'h24); 
  read(16'h28); 
  read(16'h2c); 
end

endmodule

