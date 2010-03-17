PROGRAM=test

SOURCE_FILES=$(PROGRAM).java

BUILDSCRIPT=~/research/Robust/src/buildscript

#DEBUGFLAGS= -disjoint-debug-callsite Bar addSomething 1
#DEBUGFLAGS= -disjoint-debug-callsite Foo main 1

#DEBUGFLAGS= -disjoint-debug-callsite main analysisEntryMethod 1
#DEBUGFLAGS= -disjoint-debug-callsite addSomething main 1
#DEBUGFLAGS= -disjoint-debug-callsite addBar addSomething 1
#DEBUGFLAGS= -disjoint-debug-callsite Bar addBar 1
DEBUGFLAGS=

BSFLAGS= -mainclass Test -justanalyze -disjoint -disjoint-k 2 -disjoint-write-dots final -disjoint-write-ihms -disjoint-alias-file aliases.txt normal -enable-assertions

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
	$(BUILDSCRIPT) $(OLDBSFLAGS) -o $(PROGRAM) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  aliases.txt
