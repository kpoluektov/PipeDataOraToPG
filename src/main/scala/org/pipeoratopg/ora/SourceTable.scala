package org.pipeoratopg.ora

import org.pipeoratopg.Table

sealed trait SourceTable {
  var cursorRef : java.math.BigDecimal = new java.math.BigDecimal(-1)
  val tbl : Table
  val session : OraSession
  val numRows : Int
  val condition : String
  def open(columnStr: String = "") :Unit
  def close() : Unit
  def getChunk : Option[PartResp]
}
case class SourceTableXML(session: OraSession, tbl: Table, numRows: Int, condition: String) extends SourceTable{
  def open(columnStr: String): Unit = {
    cursorRef = OraTools.openTableXML(session.get(), tbl, numRows, condition, columnStr)
  }

  override def close(): Unit = {
    OraTools.closeTable(isXML = true, session.get(), cursorRef)
  }

  override def getChunk: Option[PartRespClob] = {
    OraTools.getPartCLOB(isXML = true, session.get(), cursorRef, numRows).asInstanceOf[Option[PartRespClob]]
  }
}

case class SourceTableJson(session: OraSession, tbl: Table, numRows: Int, condition: String) extends SourceTable{
  def open(columnStr: String): Unit = {
    cursorRef = OraTools.openTableJson(session.get(), tbl, numRows, condition, columnStr)
  }

  override def close(): Unit = {
    OraTools.closeTable(isXML = false, session.get(), cursorRef)
  }

  override def getChunk: Option[PartRespBlob] = {
    OraTools.getPartCLOB(isXML = false, session.get(), cursorRef, numRows).asInstanceOf[Option[PartRespBlob]]
  }
}