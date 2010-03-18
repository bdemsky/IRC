PROGRAM=test

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=../../../buildscript

DEBUGFLAGS= -disjoint-debug-callsite f1 main 1

BSFLAGS= -mainclass Test -justanalyze -disjoint -disjoint-k 2 -disjoint-write-dots all -disjoint-write-ihms -disjoint-alias-file aliases.txt normal -enable-assertions

all: $(PROGRAM).bin

view: PNGs
	eog *.png &

PNGs: DOTs
	d2p *COMPLETE*.dot

DOTs: $(PROGRAM).bin

$(PROGRAM).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(BSFLAGS) $(DEBUGFLAGS) -o $(PROGRAM) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  aliases.txt
