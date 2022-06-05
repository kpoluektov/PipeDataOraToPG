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

`POL_TEST_OBJECTS5                                  1097960         140`  
`POL_TEST_OBJECTS6                                  1097960         140`

`2 rows selected`
#### Results
`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 1097960 rows inserted. 18200 rows/sec`  
`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS6 finished. 1097960 rows inserted. 18111 rows/sec`  
`INFO [pool-1-thread-1] PipeBySizeDesc$ - Pipe finished in 61.9822263 sec`  
CPU usage ~ 100% 

### Test2
#### Data
1 table POL.POL_TEST_OBJECTS5. no LOB columns
#### Results
`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 1097960 rows inserted. 26350 rows/sec`  
`INFO [pool-1-thread-1] PipeBySizeDesc$ - Pipe finished in 43.23252 sec`
CPU usage ~ 100% 

### Test3
#### Data
1 table POL.POL_TEST_OBJECTS5 + CLOB column = empty_clob
#### Results
`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 1097960 rows inserted. 23765 rows/sec`  
`INFO [pool-1-thread-1] PipeBySizeDesc$ - Pipe finished in 47.8153166 sec`
CPU usage ~ 100% 

### Test4
#### Data
1 table POL.POL_TEST_OBJECTS5 + CLOB column = XMLForest(all columns for current row). Avg row length is 650 symbols approx.
#### Results
`INFO [pool-1-thread-2] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 1097960 rows inserted. 12243 rows/sec`  
`INFO [pool-1-thread-2] PipeBySizeDesc$ - Pipe finished in 91.2704871 sec`    
CPU usage ~ 80% 

### Test5
#### Data
1 table POL.POL_TEST_OBJECTS5 + CLOB column = Some XML. Avg row length is 5150 symbols approx.
#### Results
`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 1097960 rows inserted. 1219 rows/sec`  
`INFO [pool-1-thread-1] PipeBySizeDesc$ - Pipe finished in 905.413596 sec`
CPU usage ~ 60%   
ENV Bottleneck is "direct path read" Oracle wait event 

### Special notes
Tests were done with "CL8ISO8859P5" and "AL32UTF8" ORACLE CHARACTERSET. 
