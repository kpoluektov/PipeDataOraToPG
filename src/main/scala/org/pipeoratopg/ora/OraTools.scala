package org.pipeoratopg.ora

import org.pipeoratopg.{PipeConfig, Table}

import java.sql.{Clob, Connection, Types}
case class PartResp(rows: Int, clob: Clob)

object OraTools {

  def getPartCLOBXMLGen(connection: Connection, ctx : java.math.BigDecimal) : Option[PartResp] = {
    val res = StorageProcCallProvider.execute(
      connection,
        """declare
        |   g_ctx DBMS_XMLGEN.ctxHandle := ?;
        |   cl clob;
        |   rnum integer := 0;
        |begin
        |   cl := dbms_xmlgen.getxml(g_ctx);
        |   rnum := dbms_xmlgen.getnumrowsprocessed(g_ctx);
        |   if rnum = 0 then
        |       dbms_lob.createtemporary(cl, true, dbms_lob.call);
        |   end if;
        |   ? := cl;
        |   ? := rnum;
        |end;""".stripMargin,
      PProcedureParameterSet(
        List(
          PIn(Some(ctx), Types.NUMERIC),
          POut(Types.CLOB),
          POut(Types.INTEGER)
        )
      )
    )
    (res.head, res.last) match {
      case (c:Clob, r:Int) => Some(PartResp(r, c))
      case _ => Some(null)
    }
  }


  def openTable(connection: Connection, tbl: Table, fetchRows: Int, condition: String): java.math.BigDecimal = {
    val res = StorageProcCallProvider.execute(
      connection,
      s"""declare
         |  g_ctx DBMS_XMLGEN.ctxHandle;
         |  queryText varchar2(32767);
         |  tblOwner varchar2(30) := ?;
         |  tblName varchar2(60) := ?;
         |begin
         |   select 'select '||listagg(atc.column_name || ' ' ||${PipeConfig.XML_COLUMN_PREFIX} || column_id, ',')
         |   WITHIN GROUP (ORDER BY column_id)  ||
         |   ' from ${tbl.owner}.${tbl.name} ${if(condition.nonEmpty) " where " + condition else ""}'
         |   into queryText
         |   from all_tab_columns atc WHERE atc.owner = '${tbl.owner}' AND atc.table_name = '${tbl.name}';
         |   g_ctx := dbms_xmlgen.newcontext(queryText);
         |   dbms_xmlgen.SETCONVERTSPECIALCHARS (g_ctx, true);
         |   dbms_xmlgen.setmaxrows(g_ctx, ?);
         |   ? := g_ctx;
         |exception when others then
         |   raise_application_error('-20101', 'Parsing error for ${tbl.toEscapedString}');
         |end;""".stripMargin,
      PProcedureParameterSet(
        List(
          PIn(Some(tbl.owner), Types.VARCHAR),
          PIn(Some(tbl.name), Types.VARCHAR),
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

  def closeTableXMLGen(connection: Connection, ctx: java.math.BigDecimal): Seq[Any] = {
    StorageProcCallProvider.execute(
      connection,
      "begin\n   dbms_xmlgen.closecontext(?);\nend;",
      PProcedureParameterSet(
        List(
          PIn(Some(ctx), Types.NUMERIC),
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
}
