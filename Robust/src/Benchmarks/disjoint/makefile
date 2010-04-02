BUILDSCRIPT=~/research/Robust/src/buildscript

#################################################
##
##  To debug a call site, supply the symbols for
##  the callee, then caller, then max number of
##  analysis visits to the call site to write out
##
#################################################
#DEBUGFLAGS= -disjoint-debug-callsite setClusters innerKMeansSetting 20
#DEBUGFLAGS= -disjoint-debug-callsite ensureCapacity addElement 100


#################################################
##
##  To get snapshots (graphs) for the exit of every
##  node in a method, supply method symbol then the
##  number of analysis visits to the method to skip
##  (early visits usually uninteresting), then the
##  number of visits to take snapshots for, finally
##  a boolean value indicating if the analysis should
##  immediately terminate after the last snapshot visit
##
#################################################
#SNAPFLAGS= -disjoint-debug-snap-method calcGoodFeatureTask 5 10 true
#SNAPFLAGS= -disjoint-debug-snap-method calcGoodFeature 5 1 true
#SNAPFLAGS= -disjoint-debug-snap-method t3 1 1 true




BAMBOOFLAGS= -recover
JAVAFLAGS= -mainclass test

VISITMODE= -disjoint-dvisit-stack
#VISITMODE= -disjoint-dvisit-pqueue

DEBUGMODE= -enable-assertions -disjoint-write-dots final -disjoint-alias-file aliases.txt normal
RELEASEMODE= -disjoint-release-mode -disjoint-alias-file aliases.txt tabbed

BSFLAGS= -justanalyze -disjoint -disjoint-k 1 -flatirusermethods

all:
	echo 'pass another arg: <bamboo/bamboo-release/java/java-release>'

bamboo:
	$(BUILDSCRIPT) $(BAMBOOFLAGS) $(DEBUGMODE)   $(VISITMODE) $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java

bamboo-s:
	$(BUILDSCRIPT) $(BAMBOOFLAGS) $(DEBUGMODE) -disjoint-dvisit-stack  $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java

bamboo-q:
	$(BUILDSCRIPT) $(BAMBOOFLAGS) $(DEBUGMODE) -disjoint-dvisit-pqueue $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java


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
