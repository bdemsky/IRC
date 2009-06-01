PROGRAM=test

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -mlpdebug -nooptimize -debug -mainclass Test -ownership -ownallocdepth 1 -ownwritedots final -enable-assertions -flatirusermethods -ownaliasfile aliases.txt
#BSFLAGS= -mainclass Test -ownership -ownallocdepth 1 -ownwritedots final -enable-assertions -flatirusermethods -ownaliasfile aliases.txt


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
