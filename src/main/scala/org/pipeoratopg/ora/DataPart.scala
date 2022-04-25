package org.pipeoratopg.ora

import org.pipeoratopg.PipeConfig.XMLGEN_ROWSET

import java.io.BufferedReader
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

  override def getBody: Clob = body

  def lines(reader: BufferedReader): Iterator[String] =
    Iterator.continually(reader.readLine()).takeWhile(_ != null)

  def getBodyAsString : String = {
    if (actualRows > 0) {
      val a = new BufferedReader(body.getCharacterStream)
      lines(a).mkString
    } else "<"+XMLGEN_ROWSET+"/>"
  }

  def hasNext : Boolean = {
    actualRows == partSize
  }
}

case class DataPartBlob(body : Blob, partSize: Int) extends AbstractDataPart  {
  override def getBody: Blob = body
  def hasNext : Boolean = true
}

class DataPartIterable[B <: AbstractDataPart](tConn: Connection, fetchRows: Int, partType : String)
                                                                            extends Iterable[AbstractDataPart]{
  override def iterator: Iterator[AbstractDataPart] = {
    var current : AbstractDataPart = DataPartEmpty()
    new Iterator[AbstractDataPart] {
      override def hasNext: Boolean = current.hasNext
      override def next(): AbstractDataPart = {
        current = partType match{
          case "Clob" => val p = OraTools.getPartCLOBXMLGen(tConn).get
            DataPartClob(p.clob, p.rows, fetchRows)
          case "Blob"  => throw new Exception("Not implemented yet") //DataPartBlob(OraTools.getPartBLOB(tConn, cursor).get, fetchRows)
          case _ => throw new Exception("Unknown part type!");
        }
        current
      }
    }
  }
}
