set terminal postscript enhanced eps color

set style data linespoints
set style fill solid border -1
set ylabel 'Time in secs'
set grid ytics
set xrange [0:10]


##### Plot Execution time for 4 threads ########
set xtics ("5" 0, "100" 1, "360" 2, "560" 3, "760" 4, "7600" 5 , "66000" 6, "76000" 7, "660000" 8, "760000" 9)
#set title "Execution times for Microbenchmark"
#set output "ExecTime.eps"
#set ylabel 'Time in secs'
#set xlabel 'Desired abort rate for benchmark in %'
#plot 0.82 title 'No-lock base time', \
#    "data1.file" using 2 title 'exec time'

##### Plot Target abort rate vs observed rate for 4 threads ########
set title "Abort Rates for Microbenchmark"
set output "AbortRate.eps"
set ylabel 'Observed Abort rate in %(numabort+numsoftabort)/numcommit'
set xlabel 'Desired abort rate for benchmark in %'
plot 13.172 title 'Base abort rate', \
    "data1.file" using 3 title 'abort rate'
       

#pause -1
