package org.pipeoratopg.ora

import java.io.ByteArrayOutputStream
import java.sql.{Clob, Connection, Types}
import org.slf4j.{Logger, LoggerFactory}

class ProcedureParameter()

case class POut(parameterType: Int) extends ProcedureParameter
object GPOut

case class PIn(value: Option[Any], parameterType: Int) extends ProcedureParameter
object GPIn

case class PInOut(value: Option[Any], parameterType: Int) extends ProcedureParameter
object GPInOut

case class PProcedureParameterSet(parameters: List[ProcedureParameter])
object GPProcedureParameterSet


object StorageProcCallProvider {
  private val log: Logger = LoggerFactory.getLogger(getClass)
  def execute(connection: Connection, sql: String, ps: PProcedureParameterSet): Seq[Any] = {
    val cs = connection.prepareCall(sql)
    var index = 0
    // to register out pars and to bind in ones
    for (parameter <- ps.parameters) {
      index = index + 1
      parameter match {
        case p: POut => cs.registerOutParameter(index, p.parameterType)
        case PIn(None, t) => cs.setNull(index, t)
        case PIn(v: Some[_], Types.NUMERIC) => cs.setBigDecimal(index, v.get.asInstanceOf[java.math.BigDecimal])
        case PIn(v: Some[_], Types.BIGINT) => cs.setLong(index, v.get.asInstanceOf[Long])
        case PIn(v: Some[_], Types.INTEGER) => cs.setInt(index, v.get.asInstanceOf[Int])
        case PIn(v: Some[_], Types.VARCHAR | Types.LONGVARCHAR) => cs.setString(index, v.get.asInstanceOf[String])
        case PIn(v: Some[_], Types.CHAR) => cs.setString(index, v.get.asInstanceOf[String].head.toString)
//        case PIn(v: Some[_], OracleTypes.CURSOR) => oraCs.setCursor(index, v.get.asInstanceOf[ResultSet])
        case PIn(v: Some[_], Types.CLOB) =>
          val clob : Clob = connection.createClob()
          val s = clob.setAsciiStream(0L)
          s.write(v.get.asInstanceOf[ByteArrayOutputStream].toByteArray)
          cs.setClob(index, clob)
          s.close()
        case PInOut(None, t) => cs.setNull(index, t)
        case PInOut(v: Some[_], Types.DECIMAL) =>
          cs.setBigDecimal(index, v.get.asInstanceOf[java.math.BigDecimal])
          cs.registerOutParameter(index, Types.DECIMAL)
        case PInOut(v: Some[_], Types.BIGINT) =>
          cs.setLong(index, v.get.asInstanceOf[Long])
          cs.registerOutParameter(index, Types.BIGINT)
        case PInOut(v: Some[_], Types.INTEGER) =>
          cs.setInt(index, v.get.asInstanceOf[Int])
          cs.registerOutParameter(index, Types.INTEGER)
        case PInOut(v: Some[_], Types.REF_CURSOR) =>
          cs.setString(index, v.get.asInstanceOf[String])
          cs.registerOutParameter(index, Types.REF_CURSOR)
        case PInOut(v: Some[_], Types.VARCHAR) =>
          cs.setString(index, v.get.asInstanceOf[String])
          cs.registerOutParameter(index, Types.VARCHAR)
        case PInOut(v: Some[_], Types.LONGVARCHAR) =>
          cs.setString(index, v.get.asInstanceOf[String])
          cs.registerOutParameter(index, Types.LONGVARCHAR)
        case PInOut(v: Some[_], Types.CHAR) =>
          cs.setString(index, v.get.asInstanceOf[String].head.toString)
          cs.registerOutParameter(index, Types.CHAR)
        case _ => log.debug(s"Failed to match ProcedureParameter in executeFunction (IN): index {} ({})", index, parameter.toString)
      }
    }

    cs.executeUpdate()

    // Parse output pars

    index = 0

    val results: List[Any] = for (parameter <- ps.parameters) yield {
      index = index + 1
      parameter match {
        case PIn(_, _) => // skip PIn
        case POut(Types.NUMERIC) | POut(Types.DECIMAL) => cs.getBigDecimal(index)
        case POut(Types.BIGINT) => cs.getLong(index)
        case POut(Types.INTEGER) => cs.getInt(index)
        case POut(Types.VARCHAR | Types.LONGVARCHAR | Types.CHAR) => cs.getString(index)
        case POut(Types.CLOB) => cs.getClob(index)
        case POut(Types.BLOB) => cs.getBlob(index)
        case PInOut(_: Some[_], Types.NUMERIC | Types.DECIMAL) => cs.getInt(index)
        case PInOut(_: Some[_], Types.BIGINT) => cs.getLong(index)
        case PInOut(_: Some[_], Types.INTEGER) => cs.getInt(index)
        case PInOut(_: Some[_], Types.VARCHAR | Types.LONGVARCHAR | Types.CHAR) => cs.getString(index)
        case PInOut(_: Some[_], Types.CLOB) => cs.getClob(index)
        case PInOut(_: Some[_], Types.BLOB) => cs.getBlob(index)
        case PInOut(_: Some[_], Types.REF_CURSOR) => cs.getString(index)
        case _ =>
          log.error(s"Failed to match ProcedureParameter in executeFunction (OUT): index $index (${parameter.toString})")
      }
    }
    cs.close()

    // Return the function return parameters (there should always be one, the caller will get a List with as many return
    // parameters as we receive):

    results.filter(_ != ())
  }
}
