package beethoven.Protocol.RoCC.Helpers

import org.chipsalliance.cde.config.Parameters
import chisel3._
import chisel3.util.log2Up
import beethoven._
import beethoven.common.Misc.round2Pow2
import org.chipsalliance.diplomacy.AddressSet

object BeethovenConsts {

  def InternalCommandSizeBytes()(implicit p: Parameters): Int = round2Pow2(
    AccelRoccCommand.packLengthBytes
  )

  def getInternalCmdRoutingAddressSet(
      systemID: Int
  )(implicit p: Parameters): AddressSet =
    AddressSet(
      systemID * InternalCommandSizeBytes(),
      InternalCommandSizeBytes() - 1
    )

  def getInternalCmdRoutingAddress(systemID: UInt)(implicit
      p: Parameters
  ): UInt =
    systemID * InternalCommandSizeBytes().U

  def getInternalCmdRoutingAddressWidth()(implicit p: Parameters): Int =
    log2Up((p(AcceleratorSystems).size + 1) * InternalCommandSizeBytes())

}
