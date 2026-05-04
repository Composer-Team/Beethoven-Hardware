package beethoven.cli

import beethoven.{AcceleratorConfig, Manifest}
import io.github.classgraph.ClassGraph

import scala.jdk.CollectionConverters._

/** Locates the user's [[AcceleratorConfig]] subclass on the compile classpath.
  *
  * Beethoven projects must contain exactly one. Heterogeneity within a design
  * (multiple core kinds) lives inside the single config's
  * `List[AcceleratorSystemConfig]`, so the project-level rule stays simple.
  */
object AcceleratorDiscovery {

  def findUnique(srcDir: String): AcceleratorConfig = {
    val candidates = scanCandidates()
    candidates match {
      case Nil =>
        Manifest.err(
          s"No AcceleratorConfig subclass found on the classpath.\n" +
            s"  Define one in `$srcDir/`, e.g.:\n" +
            "    class MyDesign extends AcceleratorConfig(List(\n" +
            "      AcceleratorSystemConfig(...)\n" +
            "    ))"
        )
      case single :: Nil =>
        instantiate(single)
      case multiple =>
        Manifest.err(
          "Multiple AcceleratorConfig subclasses found on the classpath:\n  " +
            multiple.mkString("\n  ") +
            "\n\n  Beethoven projects must contain exactly one. Split into separate\n" +
            "  projects (one Beethoven.toml each)."
        )
    }
  }

  // -- private ---------------------------------------------------------------

  private def scanCandidates(): List[String] = {
    val scan = new ClassGraph()
      .enableClassInfo()
      .scan()
    try {
      val all = scan
        .getSubclasses(classOf[AcceleratorConfig].getName)
        .asScala
        .toList
      // Skip abstract classes (mid-hierarchy bases users may introduce) and
      // framework-shipped concrete subclasses (e.g. AWSF2XDMAWorkarounds in
      // beethoven.Platforms.FPGA.Xilinx.AWS) — those are helpers meant to be
      // composed into the user's `AcceleratorConfig`, not standalone designs.
      all
        .filterNot(_.isAbstract)
        .filterNot(_.getName.startsWith("beethoven."))
        .map(_.getName)
    } finally {
      scan.close()
    }
  }

  private def instantiate(fqcn: String): AcceleratorConfig = {
    val cls =
      try Class.forName(fqcn)
      catch {
        case e: ClassNotFoundException =>
          Manifest.err(
            s"Discovered AcceleratorConfig subclass '$fqcn' but failed to load it: ${e.getMessage}"
          )
      }
    val ctor =
      try cls.getDeclaredConstructor()
      catch {
        case _: NoSuchMethodException =>
          Manifest.err(
            s"AcceleratorConfig subclass '$fqcn' must have a no-arg constructor.\n" +
              "  Use class-level fields instead of constructor params, or pass\n" +
              "  design-time integers via `runMain ... -DKEY=VAL` (see BuildArgs)."
          )
      }
    ctor.setAccessible(true)
    ctor.newInstance().asInstanceOf[AcceleratorConfig]
  }
}
