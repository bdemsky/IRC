all:
	javac -cp . -Xlint Trace.java 


run:
	java -ea Trace coreprof.dat trace.out


plot: coreprof.dat
	gnuplot plot.cmd


clean:
	rm -f *.class
	rm -f *~

