package intra

import beethoven._
import beethoven.Platforms.FPGA.Xilinx.AWS.AWSF2Platform
import beethoven.Platforms.FPGA.Xilinx.AWS.DMAHelperConfig
import beethoven.Platforms.FPGA.Xilinx.AWS.MemsetHelperConfig
import intra.DispatcherCore

class SystemConfig(numCores: Int,
                        ) extends AcceleratorConfig({
  List(
    AcceleratorSystemConfig(
      nCores = 1,
      name = "VecAddAccel",
      moduleConstructor = ModuleBuilder(p => new DispatcherCore()(p)),
      /*
      memoryChannelConfig = List(
        ReadChannelConfig("feature", dataBytes = 1),
        /*
        IntraCoreMemoryPortOutConfig(
          name = s"command_out",
          toSystem = s"VecCores",
          toMemoryPort = s"command_in",
        )*/
      ),*/
      canReceiveSoftwareCommands = true,
      canIssueCoreCommandsTo = Seq("VecCores"),
    ),

    AcceleratorSystemConfig(
      nCores = numCores,
      name = "VecCores",
      moduleConstructor = ModuleBuilder(p => new VectorAddCore()(p)),
      memoryChannelConfig = List(
        ReadChannelConfig("vec_a", dataBytes = 4),
        ReadChannelConfig("vec_b", dataBytes = 4),
        WriteChannelConfig("vec_out", dataBytes = 4),
        /*
        IntraCoreMemoryPortInConfig(
          name = f"command_in",
          dataWidthBits = 32,
          nDatas = 1,
          latency = 2,
          nChannels = 1,
          portsPerChannel = 2,
          readOnly = true,
          communicationDegree = CommunicationDegree.BroadcastAllCoresChannels
        )*/
      ),
      //only recieve commands from dispatcher core
      canReceiveSoftwareCommands = false
    ),
    new DMAHelperConfig, new MemsetHelperConfig(4))
})
    
object SystemConfig extends BeethovenBuild(new SystemConfig(6),
  buildMode = BuildMode.Simulation,
  platform = new AWSF2Platform())

object SystemConfig2 extends BeethovenBuild(new SystemConfig(1),
  buildMode = BuildMode.Synthesis,
  platform = new AWSF2Platform())

object SystemConfigKria extends BeethovenBuild(new SystemConfig(1),
buildMode = BuildMode.Synthesis,
platform = new KriaPlatform())