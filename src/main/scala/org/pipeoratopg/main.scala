package org.pipeoratopg

import com.typesafe.config.ConfigFactory
import org.pipeoratopg.ora.SourceTask

import java.io.File
import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}


object main extends App{
  implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))
  private val myConfigFile = new File("src/main/resources/test_resources.conf")
  private val fileConfig = ConfigFactory.parseFile(myConfigFile)
  val t = new Thread(new SourceTask(Some(null), fileConfig, ec))
  t.start()
}
