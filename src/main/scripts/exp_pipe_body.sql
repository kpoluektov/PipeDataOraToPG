create or replace package body exp_pipe AS 
    type table_type is record (
        table_schema varchar2(30),
        table_name varchar2(128),
        is_opened boolean,
        cur_position integer,
        table_cur integer,
        rows pls_integer
    );

    g_rows_fsize pls_integer;
    g_cur_position integer;
    g_is_opened boolean := false;
    g_ctx dbms_xmlgen.ctxHandle;

    clobxml clob := empty_clob;
    
    type table_ is record (
        table_schema varchar2(30), 
        table_name varchar2(128)
    );

    type tables_ttype is table of table_type index by varchar2(128);

    tab tables_ttype;

    function get_col_list(tbl table_) return varchar2 is
    out_str varchar2(32767);
    begin
      select listagg(atc.column_name || ' C_' || column_id, ',') WITHIN GROUP (ORDER BY column_id) into out_str 
        from all_tab_columns atc WHERE atc.owner = tbl.table_schema AND atc.table_name = tbl.table_name;
        return out_str;
    end;

    function get_table_hash(tbl table_) return varchar2 is
    begin
      return dbms_utility.get_hash_value(tbl.table_schema||tbl.table_name, 2048, 1024);
    end;

    function get_query_text(tbl table_) return varchar2 is
    begin
      return 'select XMLElement("row", XMLForest('||get_col_list(tbl)||')).getClobVal() from '||tbl.table_schema||'.'||tbl.table_name;
    end;

    function get_query_text_xmlgen(tbl table_, cond varchar2) return varchar2 is
    sql_text varchar2(32767); 
    begin
        sql_text := 'select '||get_col_list(tbl)||' from '||tbl.table_schema||'.'||tbl.table_name ;
        if length(cond) > 0 then
            sql_text := sql_text || ' where '||cond;
        end if;
      return sql_text;
    end;

    function open_table (tbl table_, rows pls_integer) return varchar2 is
        cursor_ integer;
        thash varchar2(128);
        trec table_type;
        cursor_status  integer;
    begin
        thash := get_table_hash(tbl);
        if not tab.exists(thash) then
            trec.table_schema := tbl.table_schema;
            trec.table_name := tbl.table_name;
            trec.rows := rows;
            cursor_ := dbms_sql.open_cursor; 
            begin
                dbms_sql.parse(cursor_, get_query_text(tbl), dbms_sql.native);
                cursor_status := dbms_sql.execute(cursor_);
                trec.is_opened := true;
            exception when others then
                trec.is_opened := false;
                raise_application_error('-20101', 'Parsing error for '||tbl.table_schema||'.'||tbl.table_name);
            end;            
            trec.table_cur := cursor_;
            trec.cur_position := 0;
            tab(thash) := trec;
        end if;
        return thash;
    end;  

    procedure open_table_xmlgen(tbl table_, rows pls_integer, cond varchar2) is
        trec table_type;
    begin
        g_rows_fsize := rows;
        begin
            g_ctx := dbms_xmlgen.newcontext(get_query_text_xmlgen(tbl, cond)); 
            dbms_xmlgen.SETCONVERTSPECIALCHARS (g_ctx, true);
            g_is_opened := true;
        exception when others then
            g_is_opened := false;
            raise_application_error('-20101', 'Parsing error for '||tbl.table_schema||'.'||tbl.table_name);
        end;    
        dbms_xmlgen.setmaxrows(g_ctx, rows);         
        g_cur_position := 0;
    end;        

    procedure if_exists(hash_value varchar2) is
    begin
        if not tab.exists(hash_value) then
            raise_application_error('-20102', 'Cursor nto found for hash '||hash_value);
        end if;
    end;

    procedure close_table(hash_value varchar2) is
    begin
      if_exists(hash_value);
      dbms_sql.close_cursor(tab(hash_value).table_cur);
    end;

    procedure close_table_xmlgen is
    begin
      dbms_xmlgen.closecontext(g_ctx);
    end;

    procedure close_table (tbl table_) is 
    begin
        close_table(get_table_hash(tbl));
    end; 

	function get_table_chunk_xmlgen(cl out clob) return pls_integer is
    rnum pls_integer := -1;
	begin
        clobxml := dbms_xmlgen.getxml(g_ctx);
	    rnum := dbms_xmlgen.getnumrowsprocessed(g_ctx);
        if rnum <> g_rows_fsize then
            g_is_opened := false;  --seems to become finished
        end if;
        g_cur_position := g_cur_position + rnum;
        if rnum = 0 then
            select empty_clob into clobxml from dual; 
        end if;
        cl := clobxml;
        return rnum;
	end;        

	function get_table_chunk_xmlgen return clob is
    rnum pls_integer := -1;
	begin
        rnum := get_table_chunk_xmlgen(clobxml);
        return clobxml;
	end;       

	function get_table_chunk(hash_value varchar2) return clob is
    string_table dbms_sql.clob_table;
    rnum pls_integer := -1;
    sr integer;
	begin
        if_exists(hash_value);
        if tab(hash_value).is_opened then
            sr := tab(hash_value).table_cur;
            dbms_sql.define_array(sr, 1, string_table, tab(hash_value).rows, 1);
            rnum := dbms_sql.fetch_rows(sr);
            dbms_sql.column_value(sr, 1, string_table);
            if clobxml is not null and dbms_lob.isopen(clobxml) = 1 then
                dbms_lob.freetemporary(clobxml);
            end if;
        end if;
        if rnum <> tab(hash_value).rows then
            tab(hash_value).is_opened := false;  --seems to become finished
        end if;
        tab(hash_value).cur_position := tab(hash_value).cur_position + rnum;
        if rnum > 0 then
            select XMLSerialize(CONTENT XMLElement("part", XMLATTRIBUTES(rnum AS "rows"), XMLAgg(XMLType(column_value))) as clob) 
                into clobxml from table(string_table);
        else 
            select empty_clob into clobxml from dual; 
        end if;
        return clobxml;
	end;

    function clob_to_blob (p_data clob) return  blob is
        l_blob         blob;
        l_dest_offset  pls_integer := 1;
        l_src_offset   pls_integer := 1;
        l_lang_context pls_integer := dbms_lob.default_lang_ctx;
        l_warning      pls_integer := dbms_lob.warn_inconvertible_char;
    BEGIN
        dbms_lob.createtemporary(
            lob_loc => l_blob,
            cache   => true);
        dbms_lob.converttoblob(
        dest_lob      => l_blob,
        src_clob      => p_data,
        amount        => dbms_lob.lobmaxsize,
        dest_offset   => l_dest_offset,
        src_offset    => l_src_offset, 
        blob_csid     => dbms_lob.default_csid,
        lang_context  => l_lang_context,
        warning       => l_warning);
        return  l_blob;
    end;

    function get_compressed_chunk(hash_value varchar2) return blob is
    begin
        return utl_compress.lz_compress (clob_to_blob(get_table_chunk(hash_value)));
    end;

    function is_opened(hash_value varchar2) return pls_integer is 
    begin
      if_exists(hash_value);
      if tab(hash_value).is_opened then
        return 1;
      else
        return -1;
      end if;
    end; 
    ----------------------- public members

    procedure close_table (table_schema varchar2, table_name varchar2) is 
        tab table_;
    begin
        tab.table_name := table_name;
        tab.table_schema := table_schema;
        close_table(tab);
    end;

    procedure open_table_xmlgen(table_schema varchar2, table_name varchar2, rows pls_integer default 1000, cond varchar2 default '') is
        tab table_;
    begin
        tab.table_name := table_name;
        tab.table_schema := table_schema;
        open_table_xmlgen(tab, rows, cond);
    end;

    function open_table (table_schema varchar2, table_name varchar2, rows pls_integer default 1000) return varchar2 is 
        tab table_;
    begin
        tab.table_name := table_name;
        tab.table_schema := table_schema;
        return open_table(tab, rows);
    end;

END exp_pipe;
/

SHOW ERR;
