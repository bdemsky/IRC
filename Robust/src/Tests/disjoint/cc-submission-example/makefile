PROGRAM=test

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=~/research/Robust/src/buildscript

BSFLAGS= -mainclass Test -joptimize -justanalyze -disjoint -disjoint-k 1 -enable-assertions
DEBUGFLAGS= -disjoint-debug-snap-method graphLoop 0 10 true #-disjoint-write-dots all #-disjoint-write-initial-contexts -disjoint-write-ihms 

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
	rm -f  effects.txt
