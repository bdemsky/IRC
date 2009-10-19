MAIN_CLASS=D2

PROGRAM=test
SOURCE_FILES=D2.java

BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -debug -nooptimize -mainclass $(MAIN_CLASS) 

DBCALLFLAGS= -owndebugcaller main -owndebugcallee executeAll 
#DBCALLFLAGS= -owndebugcaller executeAll -owndebugcallee executeMessage -owndebugcallcount 0
#DBCALLFLAGS= -owndebugcaller executeMessage -owndebugcallee amendFlightPlan

ANALYZEFLAGS= -justanalyze $(DBCALLFLAGS) -ownership -ownallocdepth 1 -ownwritedots final -ownaliasfile aliases.txt -enable-assertions

all: $(PROGRAM)

view: PNGs
	eog *.png &

PNGs: DOTs
	d2p *COMPLETE*.dot

DOTs: $(PROGRAM).bin

$(PROGRAM): $(SOURCE_FILES)
	$(BUILDSCRIPT) $(BSFLAGS) -o $(PROGRAM) $(SOURCE_FILES)

analyze: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(BSFLAGS) $(ANALYZEFLAGS) -o $(PROGRAM) $(SOURCE_FILES)
 
clean:
	rm -f  $(PROGRAM).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  aliases.txt
	rm -f  output.txt

