package org.pipeoratopg

import com.typesafe.config.ConfigFactory
import org.pipeoratopg.ora.{FuturedSourceTask, OraSession, OraTools}
import org.slf4j.{Logger, LoggerFactory}
import slick.jdbc.JdbcBackend.Database

import java.util.Properties
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


object PipeBySizeDesc extends App{
  val t0 = System.nanoTime()
  val log: Logger = LoggerFactory.getLogger(this.getClass)
  val props = new Properties
  val sinkDB : Database = Database.forConfig("pg", PipeConfig.config)
  val oraConn = new OraSession(PipeConfig.config)
  val oraOwner = PipeConfig.config.getString("oraOwner")

  val oraMask = PipeConfig.config.getString("oramask")

  val threadPoolSize = PipeConfig.configGetIntIfExists(PipeConfig.config, PipeConfig.NUMTHREADS_STR, 2)

  private val tablesQueue = new ConcurrentLinkedQueue[Table] // for ordered sequence
  private val tablesHash: ConcurrentHashMap[String, Table] = new ConcurrentHashMap() // Monitor when finished

  implicit val ec = new ExecutionContext {
    val threadPool: ExecutorService = Executors.newFixedThreadPool(threadPoolSize)
    def execute(runnable: Runnable): Unit = {
      threadPool.submit(runnable)
    }
    def reportFailure(t: Throwable): Unit = {}
  }

  def doFinalize(): Unit = {
    log.info("Pipe finished in {} sec", (System.nanoTime() - t0)/ 1000000000.0)
    oraConn.close()
    sinkDB.close()
    ec.threadPool.shutdown()
  }

  def doFinalize(t: Table): Unit = {
    tablesHash.remove(t.toString)// first remove table from syncMap
    if(tablesHash.isEmpty){      // check if still exists anything to do
      doFinalize()
    }
    else log.debug("not finished yet. map size is {}", tablesHash.size())
  }

  def runNextTable(): Unit = {
    var optTable : Option[Table]= Some(null)
    Future{
      if (!tablesQueue.isEmpty) {
        optTable = Some(tablesQueue.poll())
        optTable.get match {
          case t: Table =>
            val tableConfig = ConfigFactory.parseString(s"""table.name="${t.name}", table.owner="${t.owner}" """)
            val newConfig = tableConfig.withFallback(PipeConfig.config)
            val f = new FuturedSourceTask(Some(sinkDB), newConfig, oraConn)
            f.run()
          case _ => //shouldn't get here
        }
      } else log.debug("Tables queue empty")
    }.onComplete {
      case Success( t: Table) => log.debug("FuturedSourceTask for table {} finished", t)
      case Failure(e) => e.printStackTrace()
        log.error("Something went wrong for FuturedSourceTask with table {}, {}", optTable.get, e.getStackTrace.mkString)
    }
  }

  // start here
  log.info("Pipe started with mask {}", oraMask)
  oraConn.open()
  log.info("Thread pool size is {}", threadPoolSize)
  private val tables: Seq[Table] = OraTools.getTables(oraConn.get(), oraOwner, oraMask)
  log.info("Found {} tables to go", tables.size)

  tables.map{ t =>
    tablesQueue.add(t)
    tablesHash.put(t.toString, t)
  }
  //let's start {poolSize} tasks and forget
  if (tables.nonEmpty)
    for(_ <- 0 to threadPoolSize) runNextTable()
  else doFinalize()
}
