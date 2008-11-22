To compile the code type "ant jar"  in dstm2 and TransactionalIO directory


To run the integrated system run dstm2 with  
java -jar "dist/the_name_ofjar_file"


To run financialtransaction benchmark "-b "dstm2.benchmark.FinancialTransaction" is the argument to main function for dstm2

To run wordcounter benchmark "-b "dstm2.benchmark.Counter" is the argument to main function for dstm2



For the TransactionalIO the main class is TransactionalIO.benchmarks.Main
and no argumets are required.


libkooni.so path should be passed as the filepath for nativefunctions in TransactionalIO.core.ExtendedTransaction and TransactionalIO.core.TransactionalFile.

