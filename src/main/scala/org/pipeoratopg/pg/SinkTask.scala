package org.pipeoratopg.pg

import com.typesafe.config.Config
import org.pipeoratopg.PipeConfig.XMLGEN_ROWSET
import org.pipeoratopg.ora.{AbstractDataPart, DataPartClob}
import org.pipeoratopg.{Columns, PipeConfig, Table}
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

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

  def executePart(part : AbstractDataPart): Future[Int] = {

    val action  = part match {
      case c: DataPartClob =>
        db.run(
          sqlu"""with aTab as (select xml(${c.getBodyAsString}) as data)
          insert into #${PipeConfig.pgSchema}.#${tbl.pgName.get} (#${columns.toColumnList})
          SELECT #${columns.toValuesColumnList}--xmltable.*
          FROM aTab,
          XMLTABLE('//#$XMLGEN_ROWSET/ROW'
          PASSING data
            COLUMNS #${columns.toPGXMLString});""")
    }
    action
  }
}
