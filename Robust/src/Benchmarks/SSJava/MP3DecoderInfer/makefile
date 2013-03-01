BUILDSCRIPT=../../../buildscript

PROGRAM=MP3Player
SOURCE_FILES=MP3Player.java Player.java

SSJAVA= -ssjava -ssjavainfer -ssjavadebug
BSFLAGS= -32bit -mainclass $(PROGRAM)  -heapsize-mb 1350 -nooptimize -debug -garbagestats #-printlinenum #-joptimize

#NAIVEFLAG= -ssjava-infer-naive-writedots

default: $(PROGRAM)s.bin

$(PROGRAM)s.bin: $(SOURCE_FILES) makefile
	$(BUILDSCRIPT) $(SSJAVA) $(BSFLAGS) -o $(PROGRAM)s -builddir ssj $(SOURCE_FILES) $(NAIVEFLAG)

clean:
	rm -f  $(PROGRAM)s.bin $(PROGRAM)n.bin $(PROGRAM)e.bin
	rm -fr ssj norm injerr
	rm -f nve-diff.tmp nve-diff-ranges.tmp
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.pdf
	rm -f  aliases.txt
	rm -f  results*txt
	rm -f *log

