package beethoven

import beethoven._
import beethoven.common._
import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util._
import beethoven.AcceleratorBlackBoxCore._

object AcceleratorBlackBoxCore {
  val OUTPUT = true
  val INPUT = false

  class VerilogPort(
      nm: String,
      val dir: Boolean,
      val dim: Seq[Int],
      val sources: Seq[String]
  ) {
    val name = nm.trim().stripSuffix("_")

    override def toString: String = f"PORT[nm:'$nm', out:$dir, srcs: $sources]"
  }

  object VerilogPort {
    def apply(
        str: String,
        bool: Boolean,
        value: Seq[Int],
        value1: Seq[String]
    ): VerilogPort = {
      new VerilogPort(str, bool, value, value1)
    }
  }

  def getVerilogPorts(m: Iterable[VerilogPort]): String = {
    def len_of_io(L: Seq[Int]): Int =
      if (L.head == 1) 0 else 4 + Math.log10(L.head).ceil.toInt
    val longest_io = m.map(a => len_of_io(a.dim)).max
    m.map { a =>
      val dim = a.dim.map { b =>
        if (b == 1) "" else s"[${b - 1}:0]"
      }.reverse
      val dir = if (a.dir == OUTPUT) "output" else "input "
      val dim_pad = dim.head + " " * (longest_io - dim.head.length)
      s"  $dir ${dim_pad} ${a.name}${(if (dim.tail.isEmpty) ""
                                      else " ") + dim.tail.mkString("")}"
    }.mkString(",\n")
  }

  def getVerilogPortsOfSources(m: Iterable[VerilogPort]): String = {
    m.flatMap { a =>
      val dir = if (a.dir == OUTPUT) "output" else "input"
      val finalDim = a.dim.last
      val dstr = if (finalDim == 1) "\t" else s"[${finalDim - 1}:0]"
      a.sources.map { src =>
        f"  $dir $dstr ${fixBase(src)}"
      }
    }.mkString(",\n")
  }

  def getVerilogModulePortInstantiation(
      m: Iterable[VerilogPort]
  ): (String, String) = {
    def safeMkString(b: String, d: String, additive: String): String = if (
      b.isEmpty
    ) d
    else {
      if (d.isEmpty) b else b + additive + d
    }

    m.filter { a =>
      // println(a)
      !a.name.contains("__")
    }.map { a =>
      if (a.sources.length == 1) {
        // then just wire the port directly
        (s"  .${a.name}(${a.sources(0)})", "")
      } else {
        // otherwise, we need to declare an array wire, wire up the array, and use that wire
        // as the IO
        val wireName = a.name + "_wire"
        val init = s"  .${a.name}($wireName)"
        val wireDeclaration =
          f"  wire [${a.dim.head - 1}:0] $wireName${a.dim.tail.map(d => "[" + (d - 1) + ":0]").mkString("")};"
        val wireInit = a.sources.zipWithIndex
          .map { case (src, idx) =>
            s"  assign $wireName[$idx] = $src;"
          }
          .mkString("\n")
        (init, wireDeclaration + "\n" + wireInit)
      }
    }.foldLeft(("", "")) { case ((a, b), (c, d)) =>
      (safeMkString(a, c, ",\n"), safeMkString(b, d, "\n"))
    }
  }

  def getRecursiveNames(
      a: Data,
      other: Seq[(Data, String)] = Seq()
  ): Seq[(Data, String)] = {
    a match {
      case b: Bundle =>
        var acc = other
        b.elements.foreach { case (_, data) =>
          acc = getRecursiveNames(data, acc)
        }
        acc
      case v: Vec[_] =>
        var acc = other
        v.zipWithIndex.foreach { case (data, _) =>
          acc = getRecursiveNames(data, acc)
        }
        acc
      case _ => other :+ (a, a.instanceName)
    }
  }

  def fixBase(a: String): String = {
    val q = a.replace(".", "_")
    if (q.contains("[")) {
      val idx = q.indexOf("[")
      q.substring(0, q.indexOf("[")) + "_" + q.substring(
        idx + 1,
        q.indexOf("]")
      )
    } else {
      q
    }
  }

  def fix2Real(a: String): String = {
    a.replace(".", "_").replace("bits_", "")
  }

  // for reads, there are a few dimensions. The first index is the read channel name itself, the second index is
  // the channel number, and the third index (if applicable) is the vector index

  def getStructureAsPorts(
      a: Data,
      primaryDirection: Boolean,
      structureDepth: Int = 0,
      yieldSubfieldOnlyWithPrefix: Option[String] = None
  ): Iterable[VerilogPort] = {
    def getRName(s: String): String = {
      yieldSubfieldOnlyWithPrefix match {
        case None => fix2Real(s)
        case Some(t) =>
          t + "_" + s.split("\\.").takeRight(structureDepth).mkString("_")
      }
    }
    // println(a)
    a match {
      // case _: EmptyAccelResponse =>
        // Seq()
      case v: MixedVec[_] =>
        v.zipWithIndex.flatMap { case (data, _) =>
          getStructureAsPorts(
            data,
            primaryDirection,
            structureDepth + 1,
            yieldSubfieldOnlyWithPrefix
          )
        }
      case v: Vec[_] =>
        v.zipWithIndex.flatMap { case (data, _) =>
          getStructureAsPorts(
            data,
            primaryDirection,
            structureDepth + 1,
            yieldSubfieldOnlyWithPrefix
          )
        }
      case de: DecoupledIO[_] =>
        val v_iname = de.valid.instanceName
        val r_iname = de.ready.instanceName
        Seq(
          VerilogPort(
            getRName(v_iname),
            primaryDirection,
            Seq(1),
            Seq(fixBase(v_iname))
          ),
          VerilogPort(
            getRName(r_iname),
            !primaryDirection,
            Seq(1),
            Seq(fixBase(r_iname))
          )
        ) ++
          getStructureAsPorts(
            de.bits,
            primaryDirection,
            structureDepth + 1,
            yieldSubfieldOnlyWithPrefix
          )
      case b: AccelCommand =>
        b.sortedElements.flatMap { case (q, data) =>
          getStructureAsPorts(
            data,
            primaryDirection,
            structureDepth + 1,
            yieldSubfieldOnlyWithPrefix
          )
        }

      case b: Bundle =>
        b.elements.flatMap { case (q, data) =>
          getStructureAsPorts(
            data,
            primaryDirection,
            structureDepth + 1,
            yieldSubfieldOnlyWithPrefix
          )
        }
      case b =>
        Seq(
          VerilogPort(
            getRName(b.instanceName),
            primaryDirection,
            Seq(b.getWidth),
            Seq(fixBase(b.instanceName))
          )
        )

    }
  }

  def getRWChannelAsPorts[T <: Data](
      a: MixedVec[MixedVec[T]],
      ps: List[MemChannelConfig],
      prefix: String,
      primaryDirection: Boolean
  ): Iterable[VerilogPort] = {
    // first dimension corresponds directly to Channel Param names
    a.zip(ps).flatMap { case (mv: MixedVec[T], param: MemChannelConfig) =>
      val portName = param.name + "_"
      // second dimension corresponds to channel number

      val channelLen = param.nChannels

      mv.zipWithIndex.flatMap { case (v: T, idx: Int) =>
        val channelName = channelLen match {
          case 1 => ""
          case _ => "_channel" + idx
        }
        val base = portName + prefix + channelName
        // if the field is a Decoupled, we need to take apart that field
        v match {
          case d: DecoupledIO[_] =>
            val valid = d.valid.instanceName
            val ready = d.ready.instanceName
            Seq(
              VerilogPort(
                base + "_valid",
                primaryDirection,
                Seq(1),
                Seq(fixBase(valid))
              ),
              VerilogPort(
                base + "_ready",
                !primaryDirection,
                Seq(1),
                Seq(fixBase(ready))
              )
            ) ++
              (d.bits match {
                case vec: Vec[_] =>
                  val fieldNames = getRecursiveNames(vec).map(_._2)
                  val sources = fieldNames.map(fixBase)
                  Seq(
                    VerilogPort(
                      base,
                      primaryDirection,
                      Seq(1, vec(0).getWidth),
                      sources
                    )
                  )
                case _ =>
                  getStructureAsPorts(
                    d.bits,
                    primaryDirection,
                    yieldSubfieldOnlyWithPrefix = Some(base)
                  )
              })
          case d =>
            if (
              d.instanceName.contains("inProgress") || d.instanceName
                .contains("isFlushed")
            ) {
              Seq(
                VerilogPort(
                  base,
                  primaryDirection,
                  Seq(1),
                  Seq(d.instanceName.replace(".", "_"))
                )
              )
            } else
              getStructureAsPorts(v, primaryDirection)
        }
      }
    }
  }
}

class AcceleratorBlackBoxCore(blackboxBuilder: ModuleConstructor)(implicit
    p: Parameters,
    systemParams: AcceleratorSystemConfig
) extends AcceleratorCore {
  override val desiredName = systemParams.name + "Wrapper"
  val custom = blackboxBuilder.asInstanceOf[BlackboxBuilderCustom]
  val aios =
    custom.beethovenIOs.map(a => BeethovenIO(a.coreCommand, a.coreResponse))

  val readerParams = systemParams.memoryChannelConfig
    .filter(_.isInstanceOf[ReadChannelConfig])
    .map(_.asInstanceOf[ReadChannelConfig])
  val writerParams = systemParams.memoryChannelConfig
    .filter(_.isInstanceOf[WriteChannelConfig])
    .map(_.asInstanceOf[WriteChannelConfig])
  val spParams = systemParams.memoryChannelConfig
    .filter(_.isInstanceOf[ScratchpadConfig])
    .map(_.asInstanceOf[ScratchpadConfig])

  val rrio = readerParams.map(pr => getReaderModules(pr.name))
  val writerIOs = writerParams.map(pr => getWriterModules(pr.name))
  val spIOs = spParams.map(pr => getScratchpad(pr.name))

  class bb extends BlackBox {
    override val desiredName = systemParams.name + "Wrapper"
    val io = IO(new Bundle {

      val clock = Input(Clock())
      val areset = Input(Reset())

      val cmd =
        MixedVec(aios.map(aio => Flipped(Decoupled(aio.req.bits.cloneType))))
      val resp = MixedVec(aios.map(aio => Decoupled(aio.resp.bits.cloneType)))

      val read_req = MixedVec(
        rrio.map(rr =>
          MixedVec(rr._1.map(rrr => Decoupled(rrr.bits.cloneType)))
        )
      )
      val read_data = MixedVec(
        rrio.map(rr =>
          MixedVec(
            rr._2.map(rrd => Flipped(Decoupled(rrd.data.bits.cloneType)))
          )
        )
      )
      val read_inProgress =
        MixedVec(rrio.map(rr => MixedVec(rr._2.map(_ => Input(Bool())))))
      val write_req = MixedVec(
        writerIOs.map(wr =>
          MixedVec(wr._1.map(wrr => Decoupled(wrr.bits.cloneType)))
        )
      )
      val write_data = MixedVec(
        writerIOs.map(wr =>
          MixedVec(wr._2.map(wrd => Decoupled(wrd.data.bits.cloneType)))
        )
      )
      val write_isFlushed =
        MixedVec(writerIOs.map(wr => MixedVec(wr._2.map(_ => Input(Bool())))))
      val sp_req =
        MixedVec(spIOs.map(wr => Decoupled(wr.requestChannel.cloneType)))
      val sp_data_req = MixedVec(
        spIOs.map(wr =>
          MixedVec(
            wr.dataChannels.map(spd => Decoupled(spd.req.bits.cloneType))
          )
        )
      )
      val sp_data_resp = MixedVec(
        spIOs.map(sp =>
          MixedVec(
            sp.dataChannels.map(spr => Flipped(Valid(spr.res.bits.cloneType)))
          )
        )
      )
      //      sp_data.zip(spParams).foreach { case (a, b) => a.suggestName(b.name + "_data") }

    })
  }

  val impl = Module(new bb)
  impl.io.clock := clock
  impl.io.areset := reset

  val cmd_fields = getStructureAsPorts(impl.io.cmd, INPUT)
  val resp_fields = getStructureAsPorts(impl.io.resp, OUTPUT)
  val rr_fields =
    getRWChannelAsPorts(impl.io.read_req, readerParams, "req", OUTPUT)
  val rd_fields =
    getRWChannelAsPorts(impl.io.read_data, readerParams, "data", INPUT)
  val ri_fields =
    getRWChannelAsPorts(
      impl.io.read_inProgress,
      readerParams,
      "inProgress",
      INPUT
    )
  val wr_fields =
    getRWChannelAsPorts(impl.io.write_req, writerParams, "req", OUTPUT)
  val wd_fields =
    getRWChannelAsPorts(impl.io.write_data, writerParams, "data", OUTPUT)
  val wi_fields =
    getStructureAsPorts(impl.io.write_isFlushed, INPUT)
  val spr_fields =
    getRWChannelAsPorts(impl.io.sp_data_req, spParams, "req", OUTPUT)
  val spd_fields =
    getRWChannelAsPorts(impl.io.sp_data_resp, spParams, "resp", INPUT)

  impl.io.read_req.zip(rrio).foreach { case (a, b) =>
    a.zip(b._1).foreach { case (c, d) => c <> d }
  }
  impl.io.read_data.zip(rrio).foreach { case (a, b) =>
    a.zip(b._2).foreach { case (c, d) => c <> d.data }
  }
  impl.io.read_inProgress.zip(rrio).foreach { case (a, b) =>
    a.zip(b._2).foreach { case (c, d) => c <> d.in_progress }
  }
  impl.io.write_req.zip(writerIOs).foreach { case (a, b) =>
    a.zip(b._1).foreach { case (c, d) => c <> d }
  }
  impl.io.write_data.zip(writerIOs).foreach { case (a, b) =>
    a.zip(b._2).foreach { case (c, d) => c <> d.data }
  }
  impl.io.write_isFlushed.zip(writerIOs).foreach { case (a, b) =>
    a.zip(b._2).foreach { case (c, d) => c <> d.isFlushed }
  }
  impl.io.sp_data_req.zip(spIOs).foreach { case (a, b) =>
    a.zip(b.dataChannels.map(_.req)).foreach { case (c, d) => c <> d }
  }
  impl.io.sp_data_resp.zip(spIOs).foreach { case (a, b) =>
    a.zip(b.dataChannels.map(_.res)).foreach { case (c, d) => c <> d }
  }

  impl.io.sp_req.zip(spIOs).foreach { case (a, b) => a <> b.requestChannel }
  impl.io.cmd.zip(aios).foreach(a => a._1 <> a._2.req)
  impl.io.resp
    .zip(aios)
    .foreach(a => if (a._2._resp.isDefined) a._1 <> a._2.resp)

  // filter out secret fields
  val allIOs =
    (cmd_fields ++ resp_fields ++ rr_fields ++ ri_fields ++ rd_fields ++ wr_fields ++ wi_fields ++ wd_fields
      ++ spr_fields ++ spd_fields)
  val allIOs_noreserved = allIOs.filter(a => !a.name.contains("__") && !a.name.contains("_rd"))

  val bb_macro_params = {
    val params = systemParams.moduleConstructor.asInstanceOf[BlackboxBuilderCustom].verilogMacroParams 
    if (params.isEmpty) {
      ""
    } else {
      "#(parameter " + params.keySet.map { pName =>
        val v = params(pName) match {
          case s: String => f"\"$s\""
          case a => a.toString()
        }
        f"${pName}"
      }.mkString(", ") + ")"
    }
  }

  val userBB =
    f"""
       |module ${systemParams.name} (
       |  input clock,
       |  input areset,
       |
       |${getVerilogPorts(allIOs_noreserved)}
       |);
       |
       |endmodule
       |""".stripMargin

  val (portInit, wireDec) = getVerilogModulePortInstantiation(allIOs_noreserved)

  val macro_params = {
    val params = systemParams.moduleConstructor.asInstanceOf[BlackboxBuilderCustom].verilogMacroParams 
    if (params.isEmpty) {
      ""
    } else {
      "#(" + params.keySet.map { pName =>
        val v = params(pName) match {
          case s: String => f"\"$s\""
          case a => a.toString()
        }
        f".${pName}(${v})"
      }.mkString(", ") + ")"
    }
  }
  val bbWrapper =
    f"""
       |module ${this.desiredName} (
       |  input clock,
       |  input areset,
       |${getVerilogPortsOfSources(allIOs)}
       |  );
       |
       |$wireDec
       |
       |${systemParams.name} ${macro_params} ${systemParams.name}_inst (
       |  .clock(clock),
       |  .areset(areset),
       |$portInit
       |  );
       |
       |endmodule
       |
       |""".stripMargin

  // Link in Wrapper using BeethovenBuild,
  // write source to file first
  val wrapperFName =
    BeethovenBuild.hw_build_dir / s"${systemParams.name}_chiselLink.v"
  os.write.over(wrapperFName, bbWrapper)

  val bb_fpath = custom.sourcePath / s"${systemParams.name}.v"
  if (os.exists(custom.sourcePath) && !os.isDir(custom.sourcePath)) {
    throw new Exception(
      f"The provided sourcePath for ${systemParams.name} should be a " +
        f"directory where the top-level module will be stored. Got " +
        f"${custom.sourcePath.toString()}"
    )
  }
  if (!os.exists(custom.sourcePath)) {
    os.makeDir.all(custom.sourcePath)
  }
  if (!os.exists(bb_fpath)) {
    os.write(bb_fpath, userBB)
  } else {
    println(s"Found '${bb_fpath.toString()}', not overwriting")
  }
  val link_loc = BeethovenBuild.hw_build_dir / s"${systemParams.name}.v"
  if (os.exists(link_loc) && !os.isLink(link_loc)) {
    throw new Exception(
      f"Your module name (${systemParams.name}) collides with" +
        "an internal Beethoven module. Please rename."
    )
  } else if (!os.exists(link_loc)) {
    os.symlink(link_loc, bb_fpath)
  }
}
