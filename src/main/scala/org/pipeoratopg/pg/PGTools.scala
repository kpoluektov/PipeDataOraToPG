package org.pipeoratopg.pg

import org.pipeoratopg.{Columns, Table}
import org.pipeoratopg.ora.DataPartClob
import org.pipeoratopg.PipeConfig.XMLGEN_ROWSET
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{PositionedParameters, SetParameter}

import java.io.Reader

object PGTools {
  implicit val readerSetter: AnyRef with SetParameter[Reader] = SetParameter[Reader]{
    {
      case (reader:Reader, params:PositionedParameters) => params.ps.setCharacterStream(1, reader)
      case _ => throw new Exception ("Unknown param type")
    }
  }
  def getXMLTableQuery(c: DataPartClob, tbl: Table, columns: Columns, pgSchema:String) = sqlu"""with aTab as (select xml(${c.getBody}) as data)
          insert into #$pgSchema.#${tbl.pgName.get} (#${columns.toColumnList})
          SELECT #${columns.toValuesColumnList}
          FROM aTab,
          XMLTABLE('//#$XMLGEN_ROWSET/ROW'
          PASSING data
            COLUMNS #${columns.toPGXMLString});"""

  def getXPathQuery(c: DataPartClob, tbl: Table, columns: Columns, pgSchema:String) = sqlu"""with aTab as
          (select unnest(xpath('/#$XMLGEN_ROWSET/ROW', xml(${c.getBody}))) as data)
          insert into #$pgSchema.#${tbl.pgName.get} (#${columns.toColumnList})
          SELECT #${columns.toXPathSelectList}
          FROM aTab;"""
}
