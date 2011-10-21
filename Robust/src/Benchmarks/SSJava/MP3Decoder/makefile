BUILDSCRIPT=../../../buildscript

PROGRAM=MP3Player
SOURCE_FILES=MP3Player.java

SSJAVA= -ssjava -ssjavadebug
BSFLAGS= -32bit -mainclass $(PROGRAM)  -heapsize-mb 1000  -nooptimize -debug -garbagestats #-printlinenum #-joptimize
NORMAL= -ssjava-inject-error 0 0
INJECT_ERROR= -ssjava-inject-error 1000 12345


default: $(PROGRAM)s.bin

normal: $(PROGRAM)n.bin

error: $(PROGRAM)e.bin


$(PROGRAM)s.bin: $(SOURCE_FILES) makefile
	$(BUILDSCRIPT) $(SSJAVA) $(BSFLAGS) -o $(PROGRAM)s -builddir ssj $(SOURCE_FILES) 

$(PROGRAM)n.bin: $(SOURCE_FILES) makefile
	$(BUILDSCRIPT) $(NORMAL) $(BSFLAGS) -o $(PROGRAM)n -builddir norm $(SOURCE_FILES) 

$(PROGRAM)e.bin: $(SOURCE_FILES) makefile
	$(BUILDSCRIPT) $(INJECT_ERROR) $(BSFLAGS) -o $(PROGRAM)e -builddir injerr $(SOURCE_FILES) 

clean:
	rm -f  $(PROGRAM)s.bin $(PROGRAM)n.bin $(PROGRAM)e.bin
	rm -fr ssj norm injerr
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  results*txt
	rm -f *log

