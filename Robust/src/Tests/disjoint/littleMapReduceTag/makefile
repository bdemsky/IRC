BUILDSCRIPT=~/research/Robust/src/buildscript

#DEBUGFLAGS= -disjoint-debug-callsite setPartial reduceOutput 1 20 false
#SNAPFLAGS= -disjoint-debug-snap-method reduceOutput 1 20 true

BAMBOOFLAGS= -recover

#VISITMODE= -disjoint-dvisit-stack
#VISITMODE= -disjoint-dvisit-pqueue
VISITMODE= -disjoint-dvisit-stack-callees-on-top

DEBUGMODE= -enable-assertions -disjoint-write-dots all -disjoint-alias-file aliases.txt normal -disjoint-desire-determinism -disjoint-debug-scheduling

BSFLAGS= -justanalyze -disjoint -disjoint-k 1 #-flatirusermethods #-flatirtasks

bamboo:
	$(BUILDSCRIPT) $(BAMBOOFLAGS) $(DEBUGMODE)   $(VISITMODE) $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java

clean:
	rm -f  *.bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.aux
	rm -f  *.log
	rm -f  *.pdf
	rm -f  aliases.txt
	rm -f  tabResults.tex
