create schema test;

CREATE TABLE test.pol_test_objects (
	owner varchar(128) NOT NULL,
	object_name varchar(128) NOT NULL,
	subobject_name varchar(128) NULL,
	object_id numeric NOT NULL,
	data_object_id numeric NULL,
	object_type varchar(23) NULL,
	created date NOT NULL,
	last_ddl_time timestamp NOT NULL,
	timestamp varchar(19) NULL,
	status varchar(7) NULL,
	temporary varchar(1) NULL,
	generated varchar(1) NULL,
	secondary varchar(1) NULL,
	namespace numeric NOT NULL,
	edition_name varchar(128) NULL,
	sharing varchar(18) NULL,
	editionable varchar(1) NULL,
	oracle_maintained varchar(1) NULL,
	application varchar(1) NULL,
	default_collation varchar(100) NULL,
	duplicated varchar(1) NULL,
	sharded varchar(1) NULL,
	created_appid numeric NULL,
	created_vsnid numeric NULL,
	modified_appid numeric NULL,
	modified_vsnid numeric NULL
);

create table test."POL_TEST_OBJECTS#10" AS SELECT ao.*, 1 "COL#ID1" FROM test.pol_test_objects ao where 1 <> 1;

CREATE TABLE test.type_test_table (
	id int4 NULL,
	code char(3) NULL,
	name varchar(160) NULL,
	dt date NULL,
	tt timestamp NULL,
	bl bytea NULL,
	cl text NULL,
	rw bytea NULL,
	nm82 numeric(8, 2) NULL,
	nm80 numeric(8) NULL,
	namen varchar(160) NULL,
	fl float8 NULL
);
