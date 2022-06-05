package org.pipeoratopg

import org.pipeoratopg.pg.{PGName, PGType}
import scala.collection.mutable

case class Column(oraName: String, oraType: String, var xmlName: String,
                  dataLength: Int, pgName: PGName, var transName: String, var pgType: PGType){
  def process(isJson: Boolean): Unit = {
      if(isJson) {
        // lowering xml name due to PG issue BUG #16636: Upper case issue
        xmlName = xmlName.toLowerCase
      }
      PipeConfig.typeMapping.get(this.oraType) match {
        case Some(s:String) => this.pgType = PGType(s, 0)
        case _ =>
    }
  }
  val binaryTransform: String => String = (col: String) => this.oraType match {
    case "BLOB" | "RAW" => s"decode($col, 'hex')"
    case _ => col
  }

  def toPASSINGString : String = s"${this.pgName.get} ${this.pgType.get} PATH '${this.xmlName}'"
  def toValuesColumn : String = binaryTransform(this.pgName.get)/*{
    this.oraType match {
    case "BLOB" | "RAW" => s"decode(${if (xmlName) this.pgName.get else this.xmlName}, 'hex')"
    case _ => if (xmlName) this.pgName.get else this.xmlName
  }}*/

  def toXMLColumn : String = binaryTransform(this.xmlName)


  def toXPATHString : String = {
    this.oraType match {
      case "BLOB" | "RAW" => s"decode((xpath('/ROW/$xmlName/text()', atab.data))[1]::text, 'hex')"
      case _ => s"(xpath('/ROW/$xmlName/text()', atab.data))[1]::text::${pgType.get}"
    }
  }

}

object Column {
  def apply(oraName: String, oraType: String, xmlName: String, dataLength : Int) =
    new Column( oraName, oraType, xmlName, dataLength, PGName(oraName), null, PGType(oraType, dataLength) )
}

class Columns {

  def SetTransMap(tMap:Map[String, String]): Unit = {
    list.foreach(c =>
      c.transName = if (tMap.exists(_._1 == c.oraName))
                      tMap(c.oraName)
                    else c.oraName
    )
  }
  var list: mutable.Seq[Column] = mutable.Seq[Column]()
  def add(c: Column): Unit = this.list = this.list :+ c
  def toPGXMLString: String = list.map(_.toPASSINGString).mkString(",\n")
//  def toValuesColumnList(isXML:Boolean): String = list.map(_.toValuesColumn(isXML)).mkString(",")
  def toValuesColumnList: String = list.map(_.toValuesColumn).mkString(",")
  def toXMLColumnList: String = list.map(_.toXMLColumn).mkString(",")
  def toColumnList: String = list.map{ c => c.pgName.get}.mkString(",")
  def toXPathSelectList : String = list.map(_.toXPATHString).mkString(",\n")

  // Oracle simple like: FIRST_COL_NAME C_1 , ...
  def toOracleXMLSelectList : String =
    list.map(c => c.transName + " " + c.xmlName).mkString(",\n")
  // Oracle JSON like: C_1 value FIRST_COL_NAME, ...
  def toOracleJSONSelectList : String =
    list.map(c => "''" + c.xmlName + "'' value " + c.transName).mkString(",\n")
  // PostgreSQL json_to_recordset like:
  def toPGJSONRecordSet : String =
    list.map(c => c.xmlName + " " + c.pgType.get).mkString(",\n")
}


