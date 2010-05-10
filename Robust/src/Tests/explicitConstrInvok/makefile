PROGRAM=test

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=~/research/Robust/src/buildscript

BSFLAGS= -mainclass Test 

all: $(PROGRAM).bin

view: PNGs
	eog *.png &

PNGs: DOTs
	d2p *COMPLETE*.dot

DOTs: $(PROGRAM).bin

$(PROGRAM).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(BSFLAGS) $(DEBUGFLAGS) -o $(PROGRAM) $(SOURCE_FILES)

OLDBSFLAGS= -mainclass Test -justanalyze -ownership -ownallocdepth 1 -ownwritedots final -enable-assertions
old: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(OLDBSFLAGS) $(SOURCE_FILES) -o $(PROGRAM)

clean:
	rm -f  $(PROGRAM).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  aliases.txt
