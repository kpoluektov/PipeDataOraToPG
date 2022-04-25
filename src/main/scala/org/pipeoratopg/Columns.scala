package org.pipeoratopg

import org.pipeoratopg.pg.{PGName, PGType}

import scala.collection.mutable

case class Column(oraName: String, oraType: String, xmlName: String,
                  dataLength: Int, pgName: PGName, var pgType: PGType){
  def process(): Unit = {
      PipeConfig.typeMapping.get(this.oraType) match {
        case Some(s:String) => this.pgType = PGType(s, 0)
        case _ =>
    }
  }
  def toXPATHString : String = s"${this.pgName.get} ${this.pgType.get} PATH '${this.xmlName}'"
  def toValuesColumn : String = {
    this.oraType match {
    case "BLOB" | "RAW" => s"decode(${this.pgName.get}, 'hex')"
    case _ => this.pgName.get
  }}
}

object Column {
  def apply(oraName: String, oraType: String, xmlName: String, dataLength : Int) =
    new Column( oraName, oraType, xmlName, dataLength, PGName(oraName), PGType(oraType, dataLength) )
}

class Columns {
  var list: mutable.Seq[Column] = mutable.Seq[Column]()
  def add(c: Column): Unit = this.list = this.list :+ c
  def toPGXMLString: String = list.map(_.toXPATHString).mkString(",\n")
  def toValuesColumnList: String = list.map(_.toValuesColumn).mkString(",")
  def toColumnList: String = list.map{ c => c.pgName.get}.mkString(",")
}


