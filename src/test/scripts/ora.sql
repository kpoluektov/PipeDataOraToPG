CREATE TABLE pol_test_objects AS SELECT * FROM all_objects;
create table pol_test_objects#10 AS SELECT ao.*, 1 COL#ID1 FROM all_objects ao where rownum < 2;
CREATE TABLE type_test_table (
 id integer,
 code char(3),
 name varchar2(160),
 dt DATE,
 tt timestamp,
 cl clob,
 bl blob,
 rw raw(16),
 nm82 number(8,2),
 nm80 number(8,0),
 namen nvarchar2(160),
 fl float);
 insert into type_test_table values (1, '111', 'Name 111', sysdate, systimestamp, empty_clob(), empty_blob(),
    utl_raw.cast_to_raw('Test 111'), 11.1, 11.0, 'Проверка связи');
 commit;
