package org.pipeoratopg.ora

import org.pipeoratopg.PipeConfig.XML_COLUMN_PREFIX
import org.pipeoratopg.{Column, Columns, Table}

import java.sql.{Blob, Clob, Connection, SQLException, Types}
sealed trait PartResp{
  def rows: Int
}
case class PartRespClob(override val rows: Int, lob: Clob) extends PartResp
case class PartRespBlob(override val rows: Int, lob: Blob) extends PartResp

object OraTools {
  val sqlGetPartXML: String =
                          """declare
                          |   g_ctx DBMS_XMLGEN.ctxHandle := ?;
                          |   cl clob;
                          |   rows integer := ?;
                          |   rnum integer := 0;
                          |begin
                          |   cl := dbms_xmlgen.getxml(g_ctx);
                          |   rnum := dbms_xmlgen.getnumrowsprocessed(g_ctx);
                          |   if rnum = 0 then
                          |       dbms_lob.createtemporary(cl, true, dbms_lob.call);
                          |   end if;
                          |   ? := cl;
                          |   ? := rnum;
                          |end;""".stripMargin

  val sqlGetPartJson: String =
                          """declare
                          |   ctx integer := ?;
                          |   row_table dbms_sql.blob_table;
                          |   rnum pls_integer := -1;
                          |   cl blob;
                          |begin
                          |   dbms_sql.define_array(ctx, 1, row_table, ?, 1);
                          |   rnum := dbms_sql.fetch_rows(ctx);
                          |   dbms_sql.column_value(ctx, 1, row_table);
                          |   if rnum > 0 then
                          |      select JSON_ARRAYAGG(column_value FORMAT JSON returning blob)
                          |          into cl from table(row_table);
                          |   else
                          |      select empty_blob into cl from dual;
                          |   end if;
                          |   ? := cl;
                          |   ? := rnum;
                          |end;""".stripMargin
  def getPartCLOB(isXML: Boolean, connection: Connection, ctx : java.math.BigDecimal, rowNum: Int) : Option[PartResp] = {
    val res = StorageProcCallProvider.execute(
      connection,
      if (isXML) sqlGetPartXML else sqlGetPartJson,
      PProcedureParameterSet(
        List(
          PIn(Some(ctx), Types.NUMERIC),
          PIn(Some(rowNum), Types.INTEGER),
          POut(if (isXML) Types.CLOB  else Types.BLOB),
          POut(Types.INTEGER)
        )
      )
    )
    (res.head, res.last) match {
      case (c:Clob, r:Int) => Some(PartRespClob(r, c))
      case (b:Blob, r:Int) => Some(PartRespBlob(r, b))
      case _ => Some(null)
    }
  }

  def openTableXML(connection: Connection,
                tbl: Table,
                fetchRows: Int,
                condition: String,
                cols: String): java.math.BigDecimal = {
    val res = StorageProcCallProvider.execute(
      connection,
      s"""declare
         |   g_ctx DBMS_XMLGEN.ctxHandle;
         |   queryText varchar2(32767);
         |begin
         |   queryText := 'select $cols from ${tbl.owner}.${tbl.name} ${if(condition.nonEmpty) " where " + condition else ""}';
         |   g_ctx := dbms_xmlgen.newcontext(queryText);
         |   dbms_xmlgen.SETCONVERTSPECIALCHARS (g_ctx, false);
         |   dbms_xmlgen.setmaxrows(g_ctx, ?);
         |   ? := g_ctx;
         |exception when others then
         |   raise_application_error('-20101', 'Parsing error for ${tbl.toEscapedString}');
         |end;""".stripMargin,
      PProcedureParameterSet(
        List(
          PIn(Some(fetchRows), Types.INTEGER),
          POut(Types.NUMERIC)
        )
      )
    )
    res.head match {
      case c : java.math.BigDecimal => c
      case _: Any => new java.math.BigDecimal(0)
    }
  }

  def openTableJson(connection: Connection,
                tbl: Table,
                fetchRows: Int,
                condition: String,
                cols: String): java.math.BigDecimal = {
    val res = StorageProcCallProvider.execute(
      connection,
         s"""declare
         |   cursor_ integer;
         |   cursor_status  integer;
         |   queryText varchar2(32767);
         |begin
         |   queryText := 'select json_object($cols absent on null returning blob )
         |   from ${tbl.owner}.${tbl.name} ${if(condition.nonEmpty) " where " + condition else ""}';
         |   cursor_ := dbms_sql.open_cursor;
         |   dbms_sql.parse(cursor_, queryText, dbms_sql.native);
         |   cursor_status := dbms_sql.execute(cursor_);
         |   ? := cursor_;
         |exception when others then
         |   raise_application_error('-20101', 'Parsing error for ${tbl.toEscapedString}');
         |end;""".stripMargin,
      PProcedureParameterSet(
        List(
          POut(Types.NUMERIC)
        )
      )
    )
    res.head match {
      case c : java.math.BigDecimal => c
      case _: Any => new java.math.BigDecimal(0)
    }
  }
  val sqlCloseXML = "begin\n   dbms_xmlgen.closecontext(?);\nend;"
  val sqlCloseJson = "begin\n   dbms_sql.close_cursor(?);\nend;"
  def closeTable(isXML : Boolean, connection: Connection, ctx: java.math.BigDecimal): Seq[Any] = {
    StorageProcCallProvider.execute(
      connection,
      if (isXML) sqlCloseXML else sqlCloseJson,
      PProcedureParameterSet(
        List(
          PIn(Some(ctx), Types.NUMERIC)
        )
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
  def getTables(connection: Connection, oraOwner: String, oraMask: String) : Seq[Table] = {
    val statement = connection.createStatement()
    var tables: Seq[Table] = Seq()
    try {
      val resultSet = statement.executeQuery(
        s"""select table_name from all_tables where owner = '$oraOwner'
           |and regexp_like(table_name, '$oraMask')  order by num_rows*avg_row_len desc nulls last""".stripMargin)
      while (resultSet.next()) {
        tables = tables :+ Table(oraOwner, resultSet.getString("table_name"))
      }
    } catch{
      case e : SQLException => throw new Exception("Can't get list of tables for owner " + oraOwner + e.printStackTrace())
    } finally {
      statement.close()
    }
    tables
  }
  def getColumns(connection: Connection, tbl: Table) : Columns = {
    val cols: Columns = new Columns()
    val statement = connection.createStatement()
    try {
      val resultSet = statement.executeQuery(
        s"""select column_name,
           |data_type, $XML_COLUMN_PREFIX||column_id xmlname,
           | data_length
           | from all_tab_columns
           | where owner = '${tbl.owner}' AND table_name = '${tbl.name}'
           | order by column_id""".stripMargin)
      while (resultSet.next()) {
        cols.add(
          Column(
            resultSet.getString("column_name"),
            resultSet.getString("data_type"),
            resultSet.getString("xmlname"),
            resultSet.getInt("data_length")
          )
        )
      }
    } catch {
      case e : SQLException => throw new Exception("Can't get columns from table " + tbl + e.printStackTrace())
    }
    finally {
      statement.close()
    }
    cols
  }
}
