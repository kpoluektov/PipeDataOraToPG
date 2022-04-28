## Oracle to PostgreSQL DataPipe

While i admire using ora2pg to migrate ddl I suffered from two annoyed little things with data migration
- a lot of time is spent on setting up parallelism. And feel the difference between dev and prod env.
- lob columns!!! I won't persuade you to store long data in / out of RDBMS storage. My tasks were to simply migrate "as is" a lot of small CLOB/BLOB columns which was a real pain due to the low-level database driver implementation.

So PipeOraToPG was designed to solve both problems. It only moves data and does it pretty fast. Anyhow in my own 
virtual environment. Performance information can be found in the performace.md file

To try PipeOraToPG you can use docker. Just

* clone project
  `git clone https://github.com/kpoluektov/PipeDataOraToPG`

* set up \src\resources\application.conf for your environment. Pay attention to "checklob" setting. It would be better 
if dba_segments is granted to the oracle user. Set "checklob" to "false" otherwise. In the latter case LOB column size 
is not taken into account what may cause memory leak for huge LOBs.  

* run it
  `docker run -v YOUR_PATH_TO_CATALOG_WITH_APPLICATION_CONF:/config -it konstantinpoluektov/pipeoratopg:latest`

In real world you'd better package application using sbt or maven and run it on bare metal.

Feel free to drop me a line if PipeOraToPG is (un) usefull or needs to be improved by adding some new features.
