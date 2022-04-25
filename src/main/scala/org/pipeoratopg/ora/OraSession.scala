package org.pipeoratopg.ora

import com.typesafe.config.Config
import org.pipeoratopg.Constants
import org.slf4j.{Logger, LoggerFactory}

import java.sql.{Connection, DriverManager}

class OraSession(config: Config) extends Constants{
  val log: Logger = LoggerFactory.getLogger(this.getClass)
  private var connection : Option[Connection] = Some(null)

  val configOra: Config = config.getConfig("ora")
  val oraURL: String = configOra.getString("url")
  val oraUser: String = configOra.getString("user")
  val oraPasswd: String = configOra.getString("password")
  val driver: String = ORACLE_DRIVER

  def open(): Unit = {
    try {
      // make the connection
      Class.forName(driver)
      connection = Some(DriverManager.getConnection(oraURL, oraUser, oraPasswd))
      log.debug("Oracle connection to {} established", oraURL)
      val st = connection.get.createStatement()
      st.execute(
        """alter session set nls_timestamp_format = 'YYYY-MM-DD HH:MI:SS.FF'
        | nls_timestamp_tz_format='YYYY-MM-DD HH:MI:SS.FF TZH:TZM'""".stripMargin)
      st.close()
    } catch {
      case e: Throwable => e.printStackTrace()
    }
  }

  def get(): Connection = connection.get

  def setModuleAction(module: String, action: String ): Unit = {
    connection.get.setClientInfo(ORACLE_MODULE, module)
    connection.get.setClientInfo(ORACLE_ACTION, action)
  }

  def close(): Unit = connection.get.close()
}
