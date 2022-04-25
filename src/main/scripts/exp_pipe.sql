create or replace package exp_pipe as
    function open_table (table_schema varchar2, table_name varchar2, rows pls_integer default 1000) return varchar2;
    function get_table_chunk(hash_value varchar2) return clob;
    function get_compressed_chunk(hash_value varchar2) return blob;	
    procedure close_table (table_schema varchar2, table_name varchar2);
    procedure close_table(hash_value varchar2);
    function is_opened(hash_value varchar2) return pls_integer;
    procedure open_table_xmlgen(table_schema varchar2, table_name varchar2, rows pls_integer default 1000, cond varchar2 default '');
    procedure close_table_xmlgen;
    function get_table_chunk_xmlgen return clob;
    function get_table_chunk_xmlgen(cl out clob) return pls_integer;
end exp_pipe;
/

SHOW ERR;