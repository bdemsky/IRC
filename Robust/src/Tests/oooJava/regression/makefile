PROGRAM1=testSingle
PROGRAM2=testMulti

SOURCE_FILES=test.java

BUILDSCRIPT=../../../buildscript

USEOOO= -ooojava 8 2  -ooodebug  
BSFLAGS= -64bit -mainclass Test -garbagestats -nooptimize -debug
DISJOINT= -disjoint -disjoint-k 1 -enable-assertions #-disjoint-desire-determinism


all: $(PROGRAM1).bin $(PROGRAM2).bin

$(PROGRAM1).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT)           $(BSFLAGS) -o $(PROGRAM1) $(SOURCE_FILES)
	rm -fr tmpbuilddirectory

$(PROGRAM2).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(USEOOO) $(BSFLAGS) -o $(PROGRAM2) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM1).bin
	rm -f  $(PROGRAM2).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
