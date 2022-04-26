#Test env

##HW
Processor	Intel(R) Core(TM) i7-8665U CPU @ 1.90GHz, 2112 Mhz, 4 Core(s), 8 Logical Processor(s)

##DB version
Oracle DB - version 19.9; Charset "CL8ISO8859P5"
PostgreSQL - version 13.6; Encoding "UTF8"

## Compete
I won't make any comparisons with other migration tools. I just name it "pretty competitive". First, my test env is pretty poor. Second, main goal is to show performance for LOB columns.   



In fact tables are copies of all_objects joined (cartesian) with `select level from dual connect by level < 20`    

#Test Results

### All tests
`pg {
numThreads = 15
maxConnections = 15
minConnections = 2
queueSize = 8000
}
numThreads = 2      
checklob = true     
fetchsize = 500000  
`  

### Test1
#### Data
2 tables POL.POL_TEST_OBJECTS5, POL.POL_TEST_OBJECTS6 in parallel. no LOB columns
`SQL> select table_name, num_rows, avg_row_len FROM all_tables where owner = 'POL' order by 1;`

`POL_TEST_OBJECTS5                                  1097940         140`  
`POL_TEST_OBJECTS6                                  1097940         140`

`2 rows selected`
#### Results
`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 1097940 rows inserted. 9636 rps`  
`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS6 finished. 1097940 rows inserted. 9577 rps`  
`INFO [pool-1-thread-1] PipeBySizeDesc$ - Pipe finished in 116.4688376 sec`
CPU usage ~ 100% 

### Test2
#### Data
1 table POL.POL_TEST_OBJECTS5. no LOB columns
#### Results
`INFO [pool-1-thread-2] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 1097940 rows inserted. 23018 rps`  
`INFO [pool-1-thread-2] PipeBySizeDesc$ - Pipe finished in 49.1779654 sec`
CPU usage ~ 100% 

### Test3
#### Data
1 table POL.POL_TEST_OBJECTS5 + CLOB column = empty_clob
#### Results
`INFO [pool-1-thread-2] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 1097940 rows inserted. 17257 rps`  
`INFO [pool-1-thread-2] PipeBySizeDesc$ - Pipe finished in 65.7302647 sec`
CPU usage ~ 100% 

### Test4
#### Data
1 table POL.POL_TEST_OBJECTS5 + CLOB column = XMLForest(all columns for current row). Avg row length is 650 symbols approx.
#### Results
`INFO [pool-1-thread-2] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 1097940 rows inserted. 7659 rps`  
`INFO [pool-1-thread-2] PipeBySizeDesc$ - Pipe finished in 145.5811677 sec`  

CPU usage ~ 80% 

### Test5
#### Data
1 table POL.POL_TEST_OBJECTS5 + CLOB column = Some XML. Avg row length is 5150 symbols approx.
#### Results
`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 1097940 rows inserted. 1131 rps`  
`INFO [pool-1-thread-1] PipeBySizeDesc$ - Pipe finished in 975.2163969 sec`

CPU usage ~ 60%   
ENV Bottleneck is "direct path read" wait event 

### Special notes
Tests were done with "CL8ISO8859P5" and "AL32UTF8" ORACLE CHARACTERSET. 
In case of "AL32UTF8" `ORA-64451: Conversion of special character to escaped character failed.` issued for some far-non-ascii symbols from IMDB dataset `https://www.imdb.com/interfaces/`. Nothing done yet to fix.  
