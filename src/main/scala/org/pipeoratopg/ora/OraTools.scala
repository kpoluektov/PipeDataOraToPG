package org.pipeoratopg.ora

import org.pipeoratopg.Table
import java.sql.{Blob, Clob, Connection, Types}
case class PartResp(rows: Int, clob: Clob)

object OraTools {

  def getPartCLOB(connection: Connection, tHash: String) : Option[Clob] = {
    val res = StorageProcCallProvider.execute(
      connection,
      "begin\n   ? := exp_pipe.get_table_chunk(?);\nend;",
      PProcedureParameterSet(
        List(
          POut(Types.CLOB),
          PIn(Some(tHash), Types.VARCHAR)
        )
      )
    )
    res.head match {
      case c:Clob => Some(c)
      case _ => Some(null)
    }
  }
  def getPartCLOBXMLGen(connection: Connection) : Option[PartResp] = {
    val res = StorageProcCallProvider.execute(
      connection,
      "begin\n   ? := exp_pipe.get_table_chunk_xmlgen( ? );\nend;",
      PProcedureParameterSet(
        List(
          POut(Types.INTEGER),
          POut(Types.CLOB)
        )
      )
    )
    (res.head, res.last) match {
      case (r:Int, c:Clob) => Some(PartResp(r, c))
      case _ => Some(null)
    }
  }

  def getPartBLOB(connection: Connection, tHash: String) : Option[Blob] = {
    val res = StorageProcCallProvider.execute(
      connection,
      "begin\n   ? := exp_pipe.get_compressed_chunk(?);\nend;",
      PProcedureParameterSet(
        List(
          POut(Types.BLOB),
          PIn(Some(tHash), Types.VARCHAR)
        )
      )
    )
    res.head match {
      case c:Blob => Some(c)
      case _ => Some(null)
    }
  }

  def openTable(connection: Connection, tbl: Table, fetchRows: Int, condition: String): Seq[Any] = {
    StorageProcCallProvider.execute(
      connection,
      "begin\n   exp_pipe.open_table_xmlgen(?, ?, ?, ?);\nend;",
      PProcedureParameterSet(
        List(
          PIn(Some(tbl.owner), Types.VARCHAR),
          PIn(Some(tbl.name), Types.VARCHAR),
          PIn(Some(fetchRows), Types.INTEGER),
          PIn(Some(condition), Types.VARCHAR)
        )
      )
    )
  }

  def closeTable(connection: Connection, tHash: String): Seq[Any] = {
    StorageProcCallProvider.execute(
      connection,
      "begin\n   exp_pipe.close_table(?);\nend;",
      PProcedureParameterSet(
        List(
          PIn(Some(tHash), Types.VARCHAR)
        )
      )
    )
  }
  def closeTableXMLGen(connection: Connection): Seq[Any] = {
    StorageProcCallProvider.execute(
      connection,
      "begin\n   exp_pipe.close_table_xmlgen;\nend;",
      PProcedureParameterSet(
        List()
      )
    )
  }
  val relSizeSQL = "select atbl.avg_row_len from all_tables atbl"

  val lobSizeSQL: String = """select atbl.avg_row_len from (
                     |  select round(atab.avg_row_len + s.tbytes/atab.num_rows) avg_row_len,
                     |  atab.owner, atab.table_name
                     |  from all_tables atab,
                     |  ( select dl.owner dlowner, dl.table_name, sum(ds.bytes) tbytes
                     |    from dba_segments ds, all_lobs dl
                     |	  where ds.owner = dl.owner and ds.segment_name = dl.SEGMENT_NAME
                     |	  group by dl.owner, dl.table_name, ds.owner
                     |	) s where s.dlowner = atab.owner and s.table_name = atab.table_name) atbl """.stripMargin

  def getFetchSize(connection: Connection, tbl: Table, withLob: Boolean = false): Int = {
    var res : Int = 0
    val statement = connection.createStatement()
    val resultSet = statement.executeQuery((if(withLob) lobSizeSQL else relSizeSQL) +
      s" where atbl.owner = '${tbl.owner}' and atbl.table_name = '${tbl.name}'")
    while ( resultSet.next() ) {
      res = resultSet.getInt("avg_row_len")
    }
    statement.close()
    res
  }

}
