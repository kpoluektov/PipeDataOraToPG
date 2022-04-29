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

  log.info("SourceTask for table '{}' created", tbl)
  var futuredConnection : Option[OraSession] = Some(null)
  var sinkTask : Option[SinkTask] = Option(null)
  var t0 : Long = 0L
  var frows = 0

  //read condition
  val conPath: String = tbl.toEscapedString + "." + CONDITION_STR
  val condition : String =
    if (config.hasPath(conPath))
      config.getString(conPath)
    else ""

  var XMLGenCtx : java.math.BigDecimal = new java.math.BigDecimal(0)

  def init(): Unit = {
    val cols: Columns = OraTools.getColumns(oraConnection.get(), tbl)
    val includeLOBSize = PipeConfig.checkLOBSize && cols.list.exists(r => r.oraType == "CLOB" || r.oraType == "BLOB")
    val rowSize = OraTools.getFetchSize(oraConnection.get(), tbl, includeLOBSize)
    log.debug("rowsize for table '{}' is {}", tbl, rowSize)
    frows = Math.max(PipeConfig.fetchSize/Math.max(rowSize, 1), 1)
    log.debug("fetchrows size for table '{}' is {}", tbl, frows)
    futuredConnection = Some(new OraSession(config))
    futuredConnection.get.open()
    XMLGenCtx = OraTools.openTable(futuredConnection.get.get(), tbl, frows, condition)
    t0 = System.nanoTime()
    sinkTask = Some(new SinkTask(globalDB, config, tbl))
    sinkTask.get.init(cols)
  }

  def run(): Table = {
    log.info("SourceTask for table '{}' started", tbl)
    val s = spool(futuredConnection.get, frows, XMLGenCtx)
    val seq = Future.sequence(s)
    seq.onComplete{
      doNext
    }
    tbl
  }

  def getPace(numRows: Int ) : String =
    if (numRows> 0) (numRows/((System.nanoTime() - t0)/TIME_DIVIDER)).round.toString + " rows/sec"
      else ""

  def doNext[T](result: T): Unit = {
    result match {
      case r : Success[List[Int]] =>
        log.info("Table {} finished. {} rows inserted. {}", tbl, r.value.sum, getPace(r.value.sum))
      case Failure(e) =>
        e.printStackTrace()
        log.error("Table {} failed with error {}", tbl, e.getMessage)
    }
    OraTools.closeTableXMLGen(futuredConnection.get.get(), XMLGenCtx)
    futuredConnection.get.close()
    PipeBySizeDesc.runNextTable()// do next
    PipeBySizeDesc.doFinalize(tbl)
  }

  def spool(conn : OraSession, rows: Int, XMLGenCtxCur:java.math.BigDecimal): Seq[Future[Int]] = {
    conn.setModuleAction(this.getClass.toString, s"$tbl")
    val data = new DataPartIterable(conn.get(), rows, "Clob", XMLGenCtxCur)
    val res = data.map(sinkTask.get.executePart(_)).toList
    res
  }
}
