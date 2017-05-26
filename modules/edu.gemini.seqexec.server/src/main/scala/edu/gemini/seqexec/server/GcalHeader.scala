package edu.gemini.seqexec.server

import edu.gemini.seqexec.model.dhs.ImageFileId
import Header._
import Header.Implicits._
import KeywordsReader._

class GcalHeader(hs: DhsClient, gcalReader: GcalKeywordReader) extends Header {
  val gcalKeywords = List(
      buildString(gcalReader.getDiffuser.orDefault, "GCALDIFF"),
      buildString(gcalReader.getFilter.orDefault, "GCALFILT"),
      buildString(gcalReader.getLamp.orDefault, "GCALLAMP"),
      buildString(gcalReader.getShutter.orDefault, "GCALSHUT")
    )

  override def sendBefore(id: ImageFileId, inst: String): SeqAction[Unit] =
    sendKeywords(id, inst, hs, gcalKeywords)

  override def sendAfter(id: ImageFileId, inst: String): SeqAction[Unit] = SeqAction(())
}

