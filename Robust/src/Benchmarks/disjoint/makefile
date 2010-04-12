BUILDSCRIPT=~/research/Robust/src/buildscript

#################################################
##
##  To debug a call site supply:
##    1. callee symbol
##    2. caller symbol
##    3. number of analysis call site visits to skip,
##       before starting to capture (early visits
##       are usually uninteresting)
##    4. number of call site visits to capture
##    5. whether to halt analysis immediately
##       after capture (or let it run on normally)
##
#################################################
#DEBUGFLAGS= -disjoint-debug-callsite addInterOutput t6 44 2 false
#DEBUGFLAGS= -disjoint-debug-callsite addElement addInterOutput 30 100 false

#DEBUGFLAGS= -disjoint-debug-callsite setPartial reduceOutput 1 20 false


#################################################
##
##  To get snapshots (graphs) for the exit of every
##  node in a method, supply:
##    1. method symbol
##    2. number of methods visits to skip,
##       before starting to capture (early visits
##       are usually uninteresting)
##    3. number of analysis method visits to capture
##    4. whether to halt analysis immediately
##       after capture (or let it run on normally)
##
#################################################
#SNAPFLAGS= -disjoint-debug-snap-method calcGoodFeatureTask 5 10 true
#SNAPFLAGS= -disjoint-debug-snap-method calcGoodFeature 5 1 true

#SNAPFLAGS= -disjoint-debug-snap-method t6 20 1 false
#SNAPFLAGS= -disjoint-debug-snap-method addInterOutput 10 50 false

#SNAPFLAGS= -disjoint-debug-snap-method reduceOutput 1 20 true
#SNAPFLAGS= -disjoint-debug-snap-method setReduceFinish 5 50 true
#SNAPFLAGS= -disjoint-debug-snap-method setPartial 1 50 true




BAMBOOFLAGS= -recover
JAVAFLAGS= -mainclass test

#VISITMODE= -disjoint-dvisit-stack
#VISITMODE= -disjoint-dvisit-pqueue
VISITMODE= -disjoint-dvisit-stack-callees-on-top

DEBUGMODE= -enable-assertions -disjoint-write-dots final -disjoint-alias-file aliases.txt normal -disjoint-desire-determinism
RELEASEMODE= -disjoint-release-mode -disjoint-alias-file aliases.txt tabbed

BSFLAGS= -justanalyze -disjoint -disjoint-k 1 -flatirusermethods -flatirtasks

all:
	echo 'pass another arg: <bamboo/bamboo-release/java/java-release>'

bamboo:
	$(BUILDSCRIPT) $(BAMBOOFLAGS) $(DEBUGMODE)   $(VISITMODE) $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java

bamboo-s:
	$(BUILDSCRIPT) $(BAMBOOFLAGS) $(DEBUGMODE) -disjoint-dvisit-stack  $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java

bamboo-q:
	$(BUILDSCRIPT) $(BAMBOOFLAGS) $(DEBUGMODE) -disjoint-dvisit-pqueue $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java

bamboo-sees:
	$(BUILDSCRIPT) $(BAMBOOFLAGS) $(DEBUGMODE) -disjoint-dvisit-stack-callees-on-top $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java


bamboo-release:
	$(BUILDSCRIPT) $(BAMBOOFLAGS) $(RELEASEMODE) $(VISITMODE) $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java

java:
	$(BUILDSCRIPT) $(JAVAFLAGS)   $(DEBUGMODE)   $(VISITMODE) $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java

java-release:
	$(BUILDSCRIPT) $(JAVAFLAGS)   $(RELEASEMODE) $(VISITMODE) $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java

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
