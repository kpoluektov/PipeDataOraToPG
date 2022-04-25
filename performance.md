#Test env

##HW
Processor	Intel(R) Core(TM) i7-8665U CPU @ 1.90GHz, 2112 Mhz, 4 Core(s), 8 Logical Processor(s)

##DB version
Oracle DB - 19.9
PostgreSQL - 13.6

## Compete
I won't make any comparisons with other migration tools. I just name it "pretty competitive". First, my test env is pretty poor. Second, main goal was to show performance for LOB columns.   


##Data
`SQL> select table_name, num_rows, avg_row_len FROM all_tables where owner = 'POL' order by 1;`

`POL_TEST_OBJECTS5                                  2690198         255`
`POL_TEST_OBJECTS6                                  2690247         255`

`2 rows selected`

In fact tables are copies  of all_objects joined (cartesian) with `select level from dual connect by LEVEL < 50`    

#Test Results

### All tests
`pg {
numThreads = 8
maxConnections = 8
minConnections = 2
queueSize = 5000
}
numThreads = 2      
checklob = true     
fetchsize = 500000  
`  

CPU usage ~ 100% approx

### Test1 
no LOB columns

`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS6 finished. 2690247 rows inserted. 7818 rps`  
`INFO [pool-1-thread-2] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 2690198 rows inserted. 7729 rps`  
`INFO [pool-1-thread-2] PipeBySizeDesc$ - Pipe finished in 349.7420079 sec`

### Test2
POL.POL_TEST_OBJECTS5 + CLOB column = empty_clob; POL.POL_TEST_OBJECTS6 + BLOB = empty_blob

`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 2690198 rows inserted. 7648 rps`  
`INFO [pool-1-thread-2] FuturedSourceTask - Table POL.POL_TEST_OBJECTS6 finished. 2690247 rows inserted. 7549 rps`  
`INFO [pool-1-thread-2] PipeBySizeDesc$ - Pipe finished in 358.1547942 sec`

### Test3
POL.POL_TEST_OBJECTS5 + CLOB column = XMLForest(all columns for current row); POL.POL_TEST_OBJECTS6 + BLOB = (utl_raw.cast_to_raw(all columns for current row))

`INFO [pool-1-thread-1] FuturedSourceTask - Table POL.POL_TEST_OBJECTS6 finished. 2690247 rows inserted. 3085 rps`  
`INFO [pool-1-thread-2] FuturedSourceTask - Table POL.POL_TEST_OBJECTS5 finished. 2690198 rows inserted. 2867 rps`  
`INFO [pool-1-thread-1] PipeBySizeDesc$ - Pipe finished in 940.2254366 sec`  
