PROGRAM=test

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -mainclass Test -disjoint -disjoint-k 1 -enable-assertions
# -disjoint-write-dots final -disjoint-alias-file aliases.txt normal -justanalyze -joptimize -flatirusermethods

all: $(PROGRAM).bin

view: PNGs
	eog *.png &

PNGs: DOTs
	d2p *COMPLETE*.dot

DOTs: $(PROGRAM).bin

$(PROGRAM).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(BSFLAGS) -o $(PROGRAM) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  aliases.txt
