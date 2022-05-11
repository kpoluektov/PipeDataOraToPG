package org.pipeoratopg.pg

case class PGType(oraType : String, dataLength : Int) {
  private val PGType = oraType match {
    case "INTEGER"                => "INTEGER"
    case "NUMBER"                 => "NUMERIC"
    case "FLOAT"                  => "FLOAT8"
    case "CHAR" | "NCHAR"         => "CHAR" + "(" + dataLength + ")"
    case "VARCHAR2" | "NVARCHAR2" => "VARCHAR"
    case "CLOB" | "XMLTYPE"       => "TEXT"
    case "BLOB" | "RAW"           => "TEXT" //Oracle provides BLOB to HEX encoding while XMLForesting columns. Was // "BYTEA"
    case "DATE"                   => "DATE"
    case "TIMESTAMP(0)" | "TIMESTAMP(3)" | "TIMESTAMP(6)" | "TIMESTAMP(9)" => "TIMESTAMP"
    case "TIMESTAMP(3) WITH TIME ZONE" | "TIMESTAMP(6) WITH TIME ZONE" => "TIMESTAMPTZ"
  }
  def get = PGType
}

