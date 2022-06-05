package org.pipeoratopg.pg

import com.typesafe.config.Config
import org.pipeoratopg.PipeConfig.xPathSelect
import org.pipeoratopg.ora.{AbstractDataPart, DataPartClobJSON, DataPartClobXML}
import org.pipeoratopg.{Columns, Table}
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.Future

class SinkTask(globalDB: Option[Database],  config: Config, tbl: Table) {
  val log: Logger = LoggerFactory.getLogger(this.getClass)
  private val db = globalDB.get match {
    case db:Database => db
    case _ => Database.forConfig("pg", config)
  }
  val pgSchema : String = config.getString("pgSchema")
  var columns :Columns = new Columns()
  var JsonFormat : Boolean = false
  log.info("SinkTask for table '{}' created", tbl)

  def closeDB(): Unit = {
    db.close()
  }
  def init(cols : Columns, isJSON: Boolean): Unit = {
    this.columns = cols
    this.JsonFormat = isJSON
    this.columns.list.foreach(_.process(JsonFormat))
    log.debug("SinkTask for table '{}' inited with column size of {}", tbl, this.columns.list.size)
  }

  def executePart(part : AbstractDataPart): Future[Int] = {
    val action  = (part, xPathSelect, JsonFormat) match {
      case (c: DataPartClobXML, true, _) => PGTools.getXPathQuery(c, tbl, columns, pgSchema)
      case (c: DataPartClobXML, false, false) => PGTools.getXMLTableQuery(c, tbl, columns, pgSchema)
      case (c: DataPartClobJSON, false, true) => PGTools.getJSONQuery(c, tbl, columns, pgSchema)
    }
    db.run(action)
  }
}
