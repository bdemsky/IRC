PROGRAM=test

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=~/research/Robust/src/buildscript

USEMLP= -mlp 1 2 # -mlpdebug # use to turn mlp on and off and make sure rest of build not broken
BSFLAGS= $(USEMLP) -nooptimize -debug -garbagestats -mainclass Test -ownership -ownallocdepth 1 -ownwritedots final -enable-assertions -flatirusermethods -ownaliasfile aliases.txt

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
