MAIN_CLASS=Tagger

PROGRAM=test
SOURCE_FILES=*.java

BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -debug -nooptimize -mainclass $(MAIN_CLASS) -justanalyze -ownership -ownallocdepth 1 -ownwritedots final -ownaliasfile aliases.txt -enable-assertions

all: run

run: $(PROGRAM).bin
#	$(PROGRAM).bin test

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
	rm -f test.index.txt
	rm -f test.tag.txt
