package org.pipeoratopg.pg

import com.typesafe.config.Config
import org.pipeoratopg.PipeConfig.XMLGEN_ROWSET
import org.pipeoratopg.ora.{AbstractDataPart, DataPartClob, DataPartEmpty}
import org.pipeoratopg.{Columns, PipeConfig, Table}
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{PositionedParameters, SetParameter}

import java.io.{BufferedReader, Reader}
import java.sql.{Clob, PreparedStatement, Types}
import scala.concurrent.{ExecutionContext, Future}

class SinkTask(globalDB: Option[Database],  config: Config, tbl: Table) {
  val log: Logger = LoggerFactory.getLogger(this.getClass)
  private val db = globalDB.get match {
    case db:Database => db
    case _ => Database.forConfig("pg", config)
  }

  var columns :Columns = new Columns()

  log.info("SinkTask for table '{}' created", tbl)

  def closeDB(): Unit = {
    db.close()
  }
  def init(cols : Columns): Unit = {
    this.columns = cols
    this.columns.list.foreach(_.process())
    log.debug("SinkTask for table '{}' inited with column size of {}", tbl, this.columns.list.size)
  }

  implicit val readerSetter: AnyRef with SetParameter[Reader] = SetParameter[Reader]{
    {
      case (reader:Reader, params:PositionedParameters) => params.ps.setCharacterStream(1, reader)
      case _ =>
    }
  }

  def executePart(part : AbstractDataPart): Future[Int] = {
    val action  = part match {
      case c: DataPartClob =>
        val s = sqlu"""with aTab as (select xml(${c.getBody}) as data)
          insert into #${PipeConfig.pgSchema}.#${tbl.pgName.get} (#${columns.toColumnList})
          SELECT #${columns.toValuesColumnList}--xmltable.*
          FROM aTab,
          XMLTABLE('//#$XMLGEN_ROWSET/ROW'
          PASSING data
            COLUMNS #${columns.toPGXMLString});"""
        db.run(s)
    }
    action
  }
}
