package intra

import chisel3._
import chisel3.util._
import beethoven._
import beethoven.common._
import chipsalliance.rocketchip.config.Parameters
import intra.VecAddCoreCommand

class DispatcherCore()(implicit p: Parameters) extends AcceleratorCore {
  val my_io = BeethovenIO(new AccelCommand("dispatch_vector_add") {
    val vec_a_addr = Address()
    val vec_b_addr = Address()
    val vec_out_addr = Address()
    val vector_length = UInt(32.W)
    val target_core = UInt(3.W)
  }, EmptyAccelResponse())
  
  val VecCoreSystemParams = p(AcceleratorSystems).filter(_.name == "VecCores")(0)
  val numCores = VecCoreSystemParams.nCores
  println(s"num cores: $numCores")

  val VecCoreIO = getIntraSysIO("VecCores", "vector_add", new VecAddCoreCommand(), new AccelRoccResponse())

  //Intrasysio (core name, command name, command object, response object)
  /**
   * provide sane default values
   */
  my_io.req.ready := false.B
  my_io.resp.valid := false.B

  VecCoreIO.req.valid := false.B
  VecCoreIO.req.bits := DontCare
  VecCoreIO.req.bits.target_core_idx := my_io.req.bits.target_core
  VecCoreIO.resp.ready := false.B

  val s_idle :: s_enqueueing :: s_working :: s_finish :: Nil = Enum(4)
  val state = RegInit(s_idle)


  switch(state){
    is(s_idle) {
      my_io.req.ready := true.B

      when(my_io.req.fire){
        state := s_enqueueing
      }
    }
    is(s_enqueueing) {
      //VecCoreIO.req.bits.target_core_idx := 1.U
      VecCoreIO.req.bits.payload.vec_a_addr := my_io.req.bits.vec_a_addr
      VecCoreIO.req.bits.payload.vec_b_addr := my_io.req.bits.vec_b_addr
      VecCoreIO.req.bits.payload.vec_out_addr := my_io.req.bits.vec_out_addr
      VecCoreIO.req.bits.payload.vector_length := my_io.req.bits.vector_length
      VecCoreIO.req.valid := true.B

      when(VecCoreIO.req.fire){
        state := s_working
      }
    }
    is(s_working) {
      VecCoreIO.resp.ready := true.B

      when(VecCoreIO.resp.fire){
        state := s_finish
      }
    }
    is(s_finish) {
      my_io.resp.valid := true.B

      when(my_io.resp.fire){
        state := s_idle
      }
    }
  }





}
