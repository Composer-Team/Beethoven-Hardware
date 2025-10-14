package beethoven

package object Protocol {

  /** Specifies the size and width of external memory ports */
  case class MasterPortParams(
      base: BigInt,
      size: BigInt,
      beatBytes: Int,
      idBits: Int,
      maxXferBytes: Int = 256,
      executable: Boolean = true
  )
  case class MemoryPortParams(
      master: MasterPortParams,
      nMemoryChannels: Int,
      incohBase: Option[BigInt] = None
  )

}
