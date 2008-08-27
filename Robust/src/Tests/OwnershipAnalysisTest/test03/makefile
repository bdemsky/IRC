PROGRAM=test03

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=~/research/Robust/src/buildscript
#BSFLAGS= -recover -flatirtasks -ownership -enable-assertions
BSFLAGS= -recover -ownership -enable-assertions

all: $(PROGRAM).bin

view: PNGs
	eog *.png

PNGs: DOTs
	#d2p *COMPLETE*.dot
	d2p *.dot	

DOTs: $(PROGRAM).bin

$(PROGRAM).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(BSFLAGS) -o $(PROGRAM) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.ps
	rm -f  *.eps
	rm -f  identifiedAliases.txt
