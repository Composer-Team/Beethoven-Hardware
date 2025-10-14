
package object beethoven {
  import org.chipsalliance.cde.config.Parameters
  import beethoven.Platforms.PlatformKey

  def platform(implicit p: Parameters) = p(PlatformKey)
}

