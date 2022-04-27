package org.pipeoratopg.ora

import com.typesafe.config.Config
import org.pipeoratopg.pg.SinkTask
import org.pipeoratopg._
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


class FuturedSourceTask(globalDB: Option[Database],
                        config: Config,
                        oraConnection: OraSession)
                       (implicit val ec: ExecutionContext ) extends Constants{

  val log: Logger = LoggerFactory.getLogger(this.getClass)
  val sourceTableConf: Config = config.getConfig("table")
  val tbl: Table = Table(sourceTableConf.getString("owner"), sourceTableConf.getString("name"))

  log.debug("SourceTask for table '{}' created", tbl)
  var futuredConnection : Option[OraSession] = Some(null)
  var sinkTask : Option[SinkTask] = Option(null)
  var t0 : Long = 0L

  //read condition
  val conPath: String = tbl.toEscapedString + "." + CONDITION_STR
  val condition : String =
    if (config.hasPath(conPath))
      config.getString(conPath)
    else ""

  def run(): Table = {
    log.info("SourceTask for table '{}' started", tbl)
    val cols: Columns = new Columns()
    val statement = oraConnection.get().createStatement()
    val resultSet = statement.executeQuery(s"""select column_name,
                                                |data_type, $XML_COLUMN_PREFIX||column_id xmlname,
                                                | data_length
                                                | from all_tab_columns
                                                | where owner = '${tbl.owner}' AND table_name = '${tbl.name}'
                                                | order by column_id""".stripMargin)
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
    statement.close()
    val includeLOBSize = PipeConfig.checkLOBSize && cols.list.exists(r => r.oraType == "CLOB" || r.oraType == "BLOB")
    val rowSize = OraTools.getFetchSize(oraConnection.get(), tbl, includeLOBSize)
    log.debug("rowsize for table '{}' is {}", tbl, rowSize)
    val frows = Math.max(PipeConfig.fetchSize/Math.max(rowSize, 1), 1)
    log.debug("fetchrows size for table '{}' is {}", tbl, frows)
    futuredConnection = Some(new OraSession(config))
    futuredConnection.get.open()
    OraTools.openTable(futuredConnection.get.get(), tbl, frows, condition)
    t0 = System.nanoTime()
    sinkTask = Some(new SinkTask(globalDB, config, tbl))
    sinkTask.get.init(cols)
    val s = spool(futuredConnection.get, frows)
    val seq = Future.sequence(s)
    seq.onComplete{
      doNext
    }
    tbl
  }

  def getPace(numRows: Int ) : String = if (numRows> 0) (numRows/((System.nanoTime() - t0)/TIME_DIVIDER)).round.toString + " rps"
      else ""

  def doNext[T](result: T): Unit = {
    result match {
      case r : Success[List[Int]] =>
        log.info("Table {} finished. {} rows inserted. {}", tbl, r.value.sum, getPace(r.value.sum))
      case Failure(e) =>
        e.printStackTrace()
        log.error("Table {} failed with error {}", tbl, e.getMessage)
    }
    OraTools.closeTableXMLGen(futuredConnection.get.get())
    futuredConnection.get.close()
    PipeBySizeDesc.runNextTable()// do next
    PipeBySizeDesc.doFinalize(tbl)
  }

  def spool(conn : OraSession, rows: Int): Seq[Future[Int]] = {
    conn.setModuleAction(this.getClass.toString, s"$tbl")
    val data = new DataPartIterable(conn.get(), rows, "Clob")
    val res = data.map(sinkTask.get.executePart/*AsPreparedStatement*/(_)).toList
    res
  }
}
