package beethoven.Generation.vcs

import beethoven.{BeethovenBuild, platform}
import org.chipsalliance.cde.config.Parameters

/** Thank you to ChiselTest for inspiring this code.
  */

object HarnessGenerator {
  private def scrape_inputs(
      strs: Seq[String],
      ins: Seq[String] = Seq(),
      outs: Seq[String] = Seq(),
      previous_line_prefix: String = ""
  ): (Seq[String], Seq[String]) = {
    val tl = strs.tail
    if (strs.head.contains(");")) (ins, outs)
    else if (strs.head.contains("module ") || strs.head.contains("(*"))
      scrape_inputs(tl, ins, outs, previous_line_prefix)
    else {
      val hd = strs.head
      val str = if (hd.contains("//")) hd.substring(0, hd.indexOf("//")).trim() else hd.trim()
      if (str == "") scrape_inputs(tl, ins, outs, previous_line_prefix)
      else if (str.contains("input "))
        scrape_inputs(tl, ins.appended(str), outs, str.split(" +").dropRight(1).mkString(" ") + " ")
      else if (str.contains("output "))
        scrape_inputs(tl, ins, outs.appended(str), str.split(" +").dropRight(1).mkString(" ") + " ")
      else
        previous_line_prefix.split(" ")(0) match {
          case "input" =>
            scrape_inputs(tl, ins.appended(previous_line_prefix + str), outs, previous_line_prefix)
          case "output" =>
            scrape_inputs(tl, ins, outs.appended(previous_line_prefix + str), previous_line_prefix)
          case _ => throw new Exception("Harness Generator error:" + hd)
        }
    }
  }

  def generateHarness()(implicit p: Parameters): Unit = {
    val r = os.read(BeethovenBuild.hw_build_dir / "BeethovenTop.sv").split("\n")

    def sanitize(q: String): Seq[String] = {
      val r =
        if (q.contains("//"))
          q.substring(0, q.indexOf("//"))
        else q

      r.replace(",", "").trim.split("[\t ]+")
    }

    def is_reserved(q: Seq[String]): Boolean = {
      val r = q.last
      r == "clock" || r == "reset" || r == "RESETn"
    }

    val (unsanitized_input, unsanitized_output) = scrape_inputs(r)

    val inputs = unsanitized_input.map(sanitize).filter(!is_reserved(_))
    val outputs = unsanitized_output.map(sanitize).filter(!is_reserved(_))

    val is_reset_active_high = platform.isActiveHighReset
    val reset_active = if (is_reset_active_high) 1 else 0
    val reset_inactive = reset_active ^ 1
    val reset_name = if (is_reset_active_high) "reset" else "RESETn"
    val ns_per_half_period = 1000.0 / platform.clockRateMHz
    val zero_width_neles = 2
    val w =
      """`timescale 1ns/1ps
        |
        |module BeethovenTopVCSHarness;
        |""".stripMargin +
        inputs
          .map { i =>
            val widthMO =
              if (i.length == zero_width_neles) 0
              else {
                val ss = i(1)
                ss.substring(1, ss.indexOf(":"))
              }
            f"  reg [$widthMO:0] ${i.last};\n"
          }
          .mkString("") + "\n" +
        outputs
          .map { i =>
            val widthMO =
              if (i.length == zero_width_neles) 0
              else {
                val ss = i(1)
                ss.substring(1, ss.indexOf(":"))
              }
            f"  wire [$widthMO:0] ${i.last};\n"
          }
          .mkString("") +
        f"""
           |  reg clock = 0;
           |  reg $reset_name = $reset_active;
           |  BeethovenTop top(
           |    .clock(clock),
           |    .$reset_name($reset_name),
           |""".stripMargin +
        (inputs ++ outputs)
          .map { q => f"    .${q.last}(${q.last})" }
          .mkString(",\n") +
        f"""
           |  );
           |
           |  reg dump_reg = 1'b0;
           |  initial begin:a1
           |    #${ns_per_half_period}
           |    forever begin
           |      #${ns_per_half_period} clock = ~clock;
           |      ;
           |    end
           |  end
           |
           |  initial begin:a2
           |`ifndef ICARUS
           |    $$vcdplusfile("BeethovenTrace.vpd");
           |`endif
           |    $$dumpvars(0, top);
           |`ifndef ICARUS
           |    $$vcdpluson;
           |`endif
           |    $$init_input_signals(clock, $reset_name, ${inputs
            .map(_.last)
            .mkString(", ")});
           |    $$init_output_signals(${outputs.map(_.last).mkString(", ")});
           |    $$init_structures;
           |  end
           |
           |  always @(negedge clock) begin:a3
           |    if (!dump_reg) begin
           |      dump_reg = 1'b1;
           |      $$dumpon;
           |    end
           |    $$tick();
           |    $$dumpflush;
           |  end
           |
           |  initial begin:a4
           |  integer i;
           |  for (i=0;i<100;i=i+1) begin
           |    # ${ns_per_half_period}
           |    ;
           |  end
           |  $reset_name = $reset_inactive;
           |  end
           |endmodule
           |
           |""".stripMargin

    os.write(BeethovenBuild.hw_build_dir / "BeethovenTopVCSHarness.v", w)
  }
}
