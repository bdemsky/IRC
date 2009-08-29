set terminal postscript enhanced eps color

set style data linespoints
set style fill solid
set grid ytics


##### Plot Execution time for 4 threads ########
#set title "Execution times for Microbenchmark numthreads= 4"
#set output "ExecTime4.eps"
#set ylabel 'Time in secs'
#set xlabel 'Desired abort rate for benchmark in %'
#set yrange [0.30:0.40]
#set style line 1 lt 2 lw 2 pt 3 ps 0.5
#plot 0.37 title 'No-lock base time' linewidth 2, \
#    "run4.data" using 1:2 title 'exec time' linetype 3 linewidth 2

#pause -1
##### Plot Target abort rate vs observed rate for 4 threads ########
#set title "Abort Rates for Microbenchmark(fudge factor= 3, numthreads= 4)"
#set output "AbortRate4.eps"
#set ylabel 'Observed Abort rate in %(numabort+numsoftabort)/numcommit'
#set xlabel 'Desired abort rate for benchmark in %'
#set yrange [0:70]
#plot "run4.txt" using 1:2 with impulse title 'Base abort rate' linewidth 2, \
#    "run4.data" using 1:3 title 'abort rate' linetype 3 linewidth 2
       
#pause -1
##### Plot Execution time for 8 threads ########
set title "Execution times for Microbenchmark numthreads= 8"
set output "ExecTime8.eps"
set ylabel 'Time in secs'
set xlabel 'Desired abort rate for benchmark in %'
set yrange [0:3.5]
set style line 1 lt 2 lw 2 pt 3 ps 0.5
plot 2.98 title 'No-lock base time' linewidth 2, \
    "run8.data" using 1:2 title 'exec time' linetype 3 linewidth 2

#pause -1
##### Plot Target abort rate vs observed rate for 8 threads ########
set title "Abort Rates for Microbenchmark(fudge factor= 3, numthreads= 8)"
set output "AbortRate8.eps"
set ylabel 'Observed Abort rate in %(numabort+numsoftabort)/numcommit'
set xlabel 'Desired abort rate for benchmark in %'
set yrange [0:70]
plot "run8.txt" using 1:2 with impulse title 'Base abort rate' linewidth 2, \
    "run8.data" using 1:3 title 'abort rate' linetype 3 linewidth 2
 
#pause -1
