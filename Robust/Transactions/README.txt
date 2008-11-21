To run the integrated system run dstm2 with  
java -jar "your_path"
with dstm2.benchmark.Main_for_Book_BenchMArk as the main class 
and "-b" "dstm2.benchmark.FinancialTransaction" as the argument to main function


For the TransactionalIO the main class is TransactionalIO.benchmarks.Main
and no argumets are required.


libkooni.so path should be passed as the filepath for nativefunctions in TransactionalIO.core.ExtendedTransaction and TransactionalIO.core.TransactionalFile.

Eventually there is gonna be java class for these files namely TransactionalIO.core.TransactionalFile.

