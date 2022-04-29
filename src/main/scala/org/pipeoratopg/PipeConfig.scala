package org.pipeoratopg

import com.typesafe.config.{Config, ConfigFactory, ConfigObject, ConfigValue}

import java.io.{File, FileNotFoundException}
import java.util.Map.Entry
import scala.jdk.CollectionConverters.CollectionHasAsScala

object PipeConfig extends Constants{
  val configGetIntIfExists : (Config, String, Int) => Int = ( conf, str, defVal ) =>
    if(conf.hasPath(str)) conf.getInt(str) else defVal

  val config: Config = System.getProperty("config.path") match {
    case s:String =>
      try {
        ConfigFactory.parseFile(new File(s + "/application.conf"))
      } catch {
        case _ : FileNotFoundException => throw new Exception(s"File $s/application.conf not found ")
      }
    case _ => ConfigFactory.load()
  }

  val fetchSize: Int = configGetIntIfExists(config, "fetchsize", 500000)

  val checkLOBSize: Boolean = config.getBoolean("checklob")

  val typeMapping : Map[String, String]= if (config.hasPath(TYPEMAPPING_STR))
    (for {
    item : ConfigObject <- config.getObjectList(TYPEMAPPING_STR).asScala
    entry : Entry[String, ConfigValue] <- item.entrySet().asScala
    string = new (String)(entry.getValue.unwrapped().toString)
    mykey = entry.getKey
  } yield (mykey, string)).toMap
  else Map()

}
