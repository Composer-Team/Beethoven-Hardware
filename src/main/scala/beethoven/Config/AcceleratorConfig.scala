package beethoven

import org.chipsalliance.cde.config._

case class AcceleratorConfig(config_lambda: AcceleratorSystemConfigList) {
  private[beethoven] def toConfig(): Config = {
    new Config((site, _, up) => { case AcceleratorSystems =>
      up(AcceleratorSystems, site) ++ config_lambda
    })
  }

  def ++(other: AcceleratorConfig): AcceleratorConfig = {
    AcceleratorConfig(config_lambda ++ other.config_lambda)
  }

  def this(lambda: Parameters => AcceleratorSystemConfig) = {
    this(List(lambda))
  }

  def this(config: AcceleratorSystemConfig) = {
    this(List((p: Parameters) => config))
  }

  def this(other: AcceleratorConfig) = {
    this(other.config_lambda)
  }

  def this(configs: List[AcceleratorSystemConfig]) = {
    this(configs.map((p: Parameters) => _))
  }
}
