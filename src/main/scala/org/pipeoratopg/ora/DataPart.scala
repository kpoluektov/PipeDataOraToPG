package org.pipeoratopg.ora

import org.pipeoratopg.PipeConfig.XMLGEN_ROWSET

import java.io.{Reader, StringReader}
import java.sql.{Blob, Clob, Connection}

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


case class DataPartClob(body : Clob, actualRows: Int, partSize: Int) extends AbstractDataPart {

  override def getBody: Reader = {
    if (actualRows > 0) body.getCharacterStream else new StringReader("<"+XMLGEN_ROWSET+"/>")
  }

  def hasNext : Boolean = {
    actualRows == partSize
  }
}

case class DataPartBlob(body : Blob, partSize: Int) extends AbstractDataPart  {
  override def getBody: Blob = body
  def hasNext : Boolean = true
}

class DataPartIterable[B <: AbstractDataPart](tConn: Connection, fetchRows: Int, partType : String, XMLGenCtx: java.math.BigDecimal)
                                                                            extends Iterable[AbstractDataPart]{
  override def iterator: Iterator[AbstractDataPart] = {
    var current : AbstractDataPart = DataPartEmpty()
    new Iterator[AbstractDataPart] {
      override def hasNext: Boolean = current.hasNext
      override def next(): AbstractDataPart = {
        current = partType match{
          case "Clob" =>  OraTools.getPartCLOBXMLGen(tConn, XMLGenCtx) match {
            case Some(p:PartResp) => DataPartClob (p.clob, p.rows, fetchRows)
//            case _ => DataPartEmpty()
          }
          case "Blob"  => throw new Exception("Not implemented yet") //DataPartBlob(OraTools.getPartBLOB(tConn, cursor).get, fetchRows)
          case _ => throw new Exception("Unknown part type!");
        }
        current
      }
    }
  }
}
