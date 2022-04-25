## Oracle to PostgreSQL DataPipe

While i admire using ora2pg to migrate ddl I suffered from two annoyed little things with data migration
- a lot of time is spent on setting up parallelism. And feel the difference between dev and prod env.
- lob columns!!! I won't persuade you to store long data in / out of RDBMS storage. My tasks were to simply migrate "as is" a lot of small CLOB/BLOB columns which was a real pain due to the low-level database driver implementation.

So PipeOraToPG was designed to solve both problems. It only moves data and does it pretty fast. Anyhow in my own virtual environment.
Performance information can be found in the performace.md file

To try PipeOraToPG you can use docker. Just

* clone project
  `git clone ...`

* create user in the original Oracle database
  `sqlplus sysuser@instance @src\main\scripts\sysaction.sql`

* install the required package   
  `sqlplus pipeoratopg/pipeoratopg@instance @src\main\scripts\useraction.sql`

* grant him access to desired schema/tables
  `grant read on my_table to pipeoratopg;`

* set up authentication details in \src\resources\application_docker.conf

* build it
  `docker build -t pipeoratopg .`

* start it
  `docker run -it pipeoratopg:latest`

In real world you'd better package application using sbt or maven and run it on bare metal.

Feel free to drop me a line if PipeOraToPG is (un) usefull or needs to be improved by adding some new features.