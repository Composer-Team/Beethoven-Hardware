package basic

import chisel3._
import chisel3.util._
import beethoven._
import beethoven.common._
import org.chipsalliance.cde.config.Parameters
import beethovenTest.TestPaths


/***
 * Test that beethoven does not fail if there are no writers on the accelerator
 ***/

class NoWriterCore(implicit p: Parameters) extends AcceleratorCore {
    val io = BeethovenIO(new AccelCommand("read_from_address") {
        val address = Address()
    }, new AccelResponse("payload_t") {
        val payload = UInt(32.W)
    })

    val s_idle :: s_wait :: s_resp :: Nil = Enum(3)
    val state = RegInit(s_idle)


    val reader = getReaderModule("reader")
    io.req.ready := state === s_idle && reader.requestChannel.ready
    reader.requestChannel.valid := state === s_idle && io.req.valid
    reader.requestChannel.bits.addr := io.req.bits.address
    reader.requestChannel.bits.len := 4.U
    reader.dataChannel.data.ready := state === s_wait

    val payload = Reg(UInt(32.W))
    io.resp.valid := state === s_resp
    io.resp.bits.payload := payload

    when (io.req.fire) {
        state := s_wait
    }

    when (reader.dataChannel.data.fire) {
        payload := reader.dataChannel.data.bits
        state := s_resp
    }

    when (io.resp.fire) {
        state := s_idle
    }
}

object NoWriterTest {
    def main(args: Array[String]): Unit = {
        BeethovenBuild.run(new AcceleratorConfig(
            new AcceleratorSystemConfig(
                nCores = 1,
                name = "noWriterCore",
                moduleConstructor = ModuleBuilder((p) => new NoWriterCore()(p)),
                memoryChannelConfig = List(new ReadChannelConfig(
                    "reader", dataBytes = 4
                )))
            ),
            platform = new KriaPlatform(),
            buildMode = BuildMode.Simulation,
            paths = TestPaths.local("NoWriterTest", BuildMode.Simulation)
        )
    }
}