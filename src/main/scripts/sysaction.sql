spool sysaction.log
create user pipeoratopg identified by pipeoratopg;
grant connect to pipeoratopg;
grant create procedure to pipeoratopg;
grant read on sys.dba_segments to pipeoratopg;
spool off;
exit