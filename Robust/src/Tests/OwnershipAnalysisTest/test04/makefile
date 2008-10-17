PROGRAM=test04

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -recover -ownership -ownaliasfile aliases.txt -enable-assertions
OUTFLAGS= -ownwritedots final -ownallocdepth 2 #-flatirusermethods

all: $(PROGRAM).bin

view: PNGs
	eog *.png &

PNGs: DOTs
	rm -f *NodeNode*.dot
	rm -f *ParameterParameter*.dot
	rm -f *Startup*.dot
	d2p *COMPLETE*.dot

DOTs: $(PROGRAM).bin

$(PROGRAM).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(BSFLAGS) $(OUTFLAGS) -o $(PROGRAM) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  aliases.txt
