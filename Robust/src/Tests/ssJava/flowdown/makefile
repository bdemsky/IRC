BUILDSCRIPT=../../../buildscript

PROGRAM=test
SOURCE_FILES=test.java

BSFLAGS= -32bit -ssjava -printlinenum -mainclass $(PROGRAM)  -heapsize-mb 1000 -garbagestats -joptimize -noloop -optimize -debug #-nooptimize #src-after-pp #-debug

default: $(PROGRAM)s.bin

$(PROGRAM)s.bin: $(SOURCE_FILES) makefile
	$(BUILDSCRIPT) $(BSFLAGS) -o $(PROGRAM)s -builddir sing $(SOURCE_FILES) 

clean:
	rm -f  $(PROGRAM)s.bin
	rm -fr sing
	rm -f tmp.c
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
	rm -f *log
	rm -f coreprof.dat
	rm -f trace.out
