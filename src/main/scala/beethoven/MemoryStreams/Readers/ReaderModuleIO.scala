package beethoven.MemoryStreams.Readers

import org.chipsalliance.diplomacy.tilelink.TLBundle

private[beethoven] trait ReaderModuleIO {
  val io: ReadChannelIO
  val tl_out: TLBundle
}
