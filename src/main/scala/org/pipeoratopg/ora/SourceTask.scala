package org.pipeoratopg.ora

import com.typesafe.config.Config
import org.pipeoratopg.pg.SinkTask
import org.pipeoratopg.{Column, Columns, Constants, Table}
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class SourceTask(globalDB: Option[Database], config: Config, implicit val context:ExecutionContext) extends Runnable with Constants{

  val log: Logger = LoggerFactory.getLogger(this.getClass)
  val sourceTableConf: Config = config.getConfig("table")
  val tbl: Table = Table(sourceTableConf.getString("owner"), sourceTableConf.getString("name"))
  val fetchRows: Int = sourceTableConf.getInt("rows")

  log.info("SourceTask for table '{}' created", tbl)
  val connection = new OraSession(config)
  var sinkTask : Option[SinkTask] = Option(null)
  val condition : String =
    if (config.hasPath(tbl.toString))
      config.getString(tbl.toString)
    else ""

  override def run(): Unit = {
    val t0 = System.nanoTime()
    log.info("SourceTask for table '{}' started", tbl)
    connection.open()
    val cols: Columns = new Columns()
    val statement = connection.get().createStatement()
      val resultSet = statement.executeQuery(s"""select column_name, data_type, $XML_COLUMN_PREFIX||rownum xmlname from all_tab_columns
                                      where owner = '${tbl.owner}' AND table_name = '${tbl.name}'
                                      order by column_id""")
      while ( resultSet.next() ) {
        cols.add(
                Column(
                        resultSet.getString("column_name"),
                        resultSet.getString("data_type"),
                        resultSet.getString("xmlname"),
                        resultSet.getInt   ("data_length")
                        )
                )
      }
      val table = OraTools.openTable(connection.get(), tbl, fetchRows, condition)

      sinkTask = Some(new SinkTask(globalDB, config, tbl))
      sinkTask.get.init(cols)

      table match {
        case _: Seq[Any] => spool()
        case _ => log.error("Something went wrong with table '{}'", tbl)
      }
//    sinkTask.get.closeDB()
    OraTools.closeTableXMLGen(connection.get())
//    connection.close()
    log.info("SourceTask for table '{}' finished in {} sec",
      tbl, (System.nanoTime() - t0)/ 1000000000.0)
  }

  def spool(): Unit = {
    connection.setModuleAction(this.getClass.toString, s"${tbl.owner}.${tbl.name}")
    val data = new DataPartIterable(connection.get(), fetchRows, "Clob")
    val res = data.map(sinkTask.get.executePart(_)).toList
    val r = Await.result(Future.sequence(res), Duration.Inf).sum
    log.info("Processed {} rows total", r)
  }
}
