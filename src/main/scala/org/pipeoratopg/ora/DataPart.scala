package org.pipeoratopg.ora

import org.pipeoratopg.PipeConfig.XMLGEN_ROWSET

import java.io.{InputStreamReader, Reader, StringReader}
import java.sql.{Blob, Clob}


sealed trait AbstractDataPart{
  def body : Any
  def getBody: Any = body
  def hasNext : Boolean
}

case class DataPartEmpty() extends AbstractDataPart{
  override def body : String = ""
  override def getBody: String = body
  def hasNext : Boolean = true
}


case class DataPartClobXML(body : Clob, actualRows: Int, partSize: Int) extends AbstractDataPart {

  override def getBody: Reader = {
    if (actualRows > 0) body.getCharacterStream else new StringReader("<"+XMLGEN_ROWSET+"/>")
  }

  def hasNext : Boolean = {
    actualRows == partSize
  }
}
case class DataPartClobJSON(body : Blob, actualRows: Int, partSize: Int) extends AbstractDataPart {
  override def getBody: Reader = {
    // Oracle provides JSON as binary stream of utf chars. So let's try to pass it as is
    if (actualRows > 0) new InputStreamReader(body.getBinaryStream) else new StringReader("[]")
  }
  def hasNext : Boolean = {
    actualRows == partSize
  }
}

case class DataPartBlob(body : Blob, partSize: Int) extends AbstractDataPart  {
  override def getBody: Blob = body
  def hasNext : Boolean = true
}

class DataPartIterable[B <: AbstractDataPart](fetchRows: Int, sTable: SourceTable)
                                                                            extends Iterable[AbstractDataPart]{
  override def iterator: Iterator[AbstractDataPart] = {
    var current : AbstractDataPart = DataPartEmpty()
    new Iterator[AbstractDataPart] {
      override def hasNext: Boolean = current.hasNext
      override def next(): AbstractDataPart = {
        current = (sTable.getChunk, sTable) match {
            case (Some(p:PartRespClob), _:SourceTableXML) =>
              DataPartClobXML (p.lob, p.rows, fetchRows)
            case (Some(p:PartRespBlob), _:SourceTableJson) =>
              DataPartClobJSON (p.lob, p.rows, fetchRows)
        }
        current
      }
    }
  }
}
