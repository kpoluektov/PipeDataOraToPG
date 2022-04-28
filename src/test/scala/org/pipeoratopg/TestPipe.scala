package org.pipeoratopg

import com.typesafe.config.ConfigFactory
import org.pipeoratopg.ora.{FuturedSourceTask, OraSession, OraTools}
import org.pipeoratopg.pg.SinkTask
import org.scalatest.FlatSpec
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import slick.jdbc.JdbcBackend.Database

import java.io.File
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}


class TestPipe extends FlatSpec with Matchers with Eventually{
  private val myConfigFile = new File("src/test/resources/application.conf")
  private val fileConfig = ConfigFactory.parseFile(myConfigFile)
  val sinkDB : Database = Database.forConfig("pg", PipeConfig.config)
  val oraConn = new OraSession(fileConfig)
  oraConn.open()
  val oraOwner: String = fileConfig.getString("oraOwner")
  val oraMask: String = fileConfig.getString("oramask")
  implicit val ec: ExecutionContextExecutor = ExecutionContext.global
  val tables: Seq[Table] = OraTools.getTables(oraConn.get(), oraOwner, oraMask)
  val tableMap: Map[String, Table] = tables.map(t => t.toString -> t).toMap

  it should "Should pipe 0 rows" in {
    val t1 = tableMap.get(oraOwner + ".POL_TEST_OBJECTS")
    val tableConfig = ConfigFactory.parseString(s"""table.name="${t1.get.name}", table.owner="${t1.get.owner}" """)
    val newConfig = tableConfig.withFallback(fileConfig)
    val fTask = new FuturedSourceTask(Some(sinkDB), newConfig, oraConn)
    val XMLGenCtx = OraTools.openTable(oraConn.get(), t1.get, 10, "rownum < 1")
    fTask.sinkTask = Some(new SinkTask(Some(sinkDB), newConfig, t1.get))
    fTask.sinkTask.get.init(OraTools.getColumns(oraConn.get(), t1.get))
    val s = fTask.spool(oraConn, 10, XMLGenCtx)
    val resTable = Await.result(Future.sequence(s), 5.seconds)
    eventually {
      resTable.sum shouldBe 0
    }
  }

  it should "Should pipe 11 rows" in {
    val t1 = tableMap.get(oraOwner + ".POL_TEST_OBJECTS")
    val tableConfig = ConfigFactory.parseString(s"""table.name="${t1.get.name}", table.owner="${t1.get.owner}" """)
    val newConfig = tableConfig.withFallback(fileConfig)
    val fTask = new FuturedSourceTask(Some(sinkDB), newConfig, oraConn)
    val XMLGenCtx = OraTools.openTable(oraConn.get(), t1.get, 10, "rownum <= 11")
    fTask.sinkTask = Some(new SinkTask(Some(sinkDB), newConfig, t1.get))
    fTask.sinkTask.get.init(OraTools.getColumns(oraConn.get(), t1.get))
    val s = fTask.spool(oraConn, 10, XMLGenCtx)
    val resTable = Await.result(Future.sequence(s), 5.seconds)
    eventually {
      resTable.sum shouldBe 11
    }
  }

  it should "Should pipe 1 row into TYPE_TEST_TABLE" in {
    val t1 = tableMap.get(oraOwner + ".TYPE_TEST_TABLE")
    val tableConfig = ConfigFactory.parseString(s"""table.name="${t1.get.name}", table.owner="${t1.get.owner}" """)
    val newConfig = tableConfig.withFallback(fileConfig)
    val fTask = new FuturedSourceTask(Some(sinkDB), newConfig, oraConn)
    val XMLGenCtx = OraTools.openTable(oraConn.get(), t1.get, 10, "")
    fTask.sinkTask = Some(new SinkTask(Some(sinkDB), newConfig, t1.get))
    fTask.sinkTask.get.init(OraTools.getColumns(oraConn.get(), t1.get))
    val s = fTask.spool(oraConn, 10, XMLGenCtx)
    val resTable = Await.result(Future.sequence(s), 5.seconds)
    eventually {
      resTable.sum shouldBe 1
    }
  }

  it should "Should pipe 1 rows into POL.POL_TEST_OBJECTS#10" in {
    val t1 = tableMap.get(oraOwner + ".POL_TEST_OBJECTS#10")
    val tableConfig = ConfigFactory.parseString(s"""table.name="${t1.get.name}", table.owner="${t1.get.owner}" """)
    val newConfig = tableConfig.withFallback(fileConfig)
    val fTask = new FuturedSourceTask(Some(sinkDB), newConfig, oraConn)
    val XMLGenCtx = OraTools.openTable(oraConn.get(), t1.get, 10, "")
    fTask.sinkTask = Some(new SinkTask(Some(sinkDB), newConfig, t1.get))
    fTask.sinkTask.get.init(OraTools.getColumns(oraConn.get(), t1.get))
    val s = fTask.spool(oraConn, 10, XMLGenCtx)
    val resTable = Await.result(Future.sequence(s), 5.seconds)
    eventually {
      resTable.sum shouldBe 1
      oraConn.close()
      sinkDB.close()
    }
  }

}
