PROGRAM=test

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=~/research/Robust/src/buildscript

BSFLAGS= -mainclass Test -justanalyze -ooojava -disjoint -disjoint-k 1 -enable-assertions
DEBUGFLAGS= -disjoint-write-dots final -disjoint-debug-snap-method main 0 10 true

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
