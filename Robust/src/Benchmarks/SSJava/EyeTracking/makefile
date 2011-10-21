BUILDSCRIPT=../../../buildscript

PROGRAM=LEA
SOURCE_FILES=LEA.java

BSFLAGS= -32bit -ssjava -ssjava-inject-error 1 12345 -mainclass $(PROGRAM)  -heapsize-mb 1000   -debug -garbagestats -ssjavadebug -printlinenum -joptimize #-optimize

default: $(PROGRAM)s.bin

$(PROGRAM)s.bin: $(SOURCE_FILES) makefile
	$(BUILDSCRIPT) $(BSFLAGS) -o $(PROGRAM) -builddir sing $(SOURCE_FILES) 

clean:
	rm -f  $(PROGRAM).bin
	rm -fr sing
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  results*txt
	rm -f *log

