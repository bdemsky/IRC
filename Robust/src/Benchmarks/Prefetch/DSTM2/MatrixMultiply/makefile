SRC = dstm2/MMulMain
RUNSRC = dstm2.MMulMain
BENCHMARK = -b dstm2.benchmark.MMulBenchMark
CP = bcel-5.2.jar
VMFLAG = -Xmx1000m

default:
	javac -cp $(CP):. $(SRC).java
run:
	java $(VMFLAG) -cp $(CP):. $(RUNSRC) $(BENCHMARK)

clean: 
	rm dstm2/*.class
	rm dstm2/exceptions/*.class
	rm dstm2/factory/*.class
	rm dstm2/benchmark/*.class
	rm dstm2/manager/*.class
	rm dstm2/util/*.class
