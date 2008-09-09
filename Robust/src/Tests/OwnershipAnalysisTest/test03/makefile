PROGRAM=test03

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -recover -ownership -ownaliasfile aliases.txt -enable-assertions
OUTFLAGS= -ownwritedots final #-flatirusermethods

all: $(PROGRAM).bin

view: PNGs
	eog *.png &

PNGs: DOTs
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
