BUILDSCRIPT=../../../buildscript

PROGRAM=MP3Player
SOURCE_FILES=MP3Player.java

BSFLAGS= -32bit -ssjava -ssjavadebug -mainclass $(PROGRAM)  -heapsize-mb 1000  -nooptimize -debug -garbagestats #-printlinenum #-joptimize

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

