MAIN_CLASS=D2

PROGRAM=test
SOURCE_FILES=D2.java

BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -debug -mainclass $(MAIN_CLASS) -flatirusermethods -flatirlibmethods -joptimize #-nooptimize

#DBCALLFLAGS= -owndebugcaller main -owndebugcallee executeAll 
#DBCALLFLAGS= -owndebugcaller executeAll -owndebugcallee executeMessage -owndebugcallcount 0
#DBCALLFLAGS= -owndebugcaller executeAll -owndebugcallee next -owndebugcallcount 0
#DBCALLFLAGS= -owndebugcaller executeAll -owndebugcallee hasNext -owndebugcallcount 0
#DBCALLFLAGS= -owndebugcaller executeAll -owndebugcallee size -owndebugcallcount 0
#DBCALLFLAGS= -owndebugcaller executeMessage -owndebugcallee getFlightList -owndebugcallcount 0
#DBCALLFLAGS= -owndebugcaller executeMessage -owndebugcallee amendFlightPlan -owndebugcallcount 0
#DBCALLFLAGS= -owndebugcaller amendFlightPlan -owndebugcallee getFlight -owndebugcallcount 0
#DBCALLFLAGS= -owndebugcaller amendFlightPlan -owndebugcallee addFix -owndebugcallcount 0
#DBCALLFLAGS= -owndebugcaller addFix -owndebugcallee addFix -owndebugcallcount 0
DBCALLFLAGS= -owndebugcaller addFix -owndebugcallee insertElementAt -owndebugcallcount 0
#DBCALLFLAGS= -owndebugcaller insertElementAt -owndebugcallee ensureCapacity -owndebugcallcount 0

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

