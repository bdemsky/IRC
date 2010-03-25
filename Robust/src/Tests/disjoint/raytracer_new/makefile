BUILDSCRIPT=../../../buildscript
MAINCLASS=test

#DEBUGFLAGS= -disjoint-debug-callsite MDRunner t3 100
#DEBUGFLAGS= -disjoint-debug-callsite calcGoodFeature calcGoodFeatureTask 100
#DEBUGFLAGS= -disjoint-debug-callsite getRows calcGoodFeature 4
#DEBUGFLAGS= -disjoint-debug-callsite setKMeans t3 500

#SNAPFLAGS= -disjoint-debug-snap-method calcGoodFeatureTask 5 10 true
#SNAPFLAGS= -disjoint-debug-snap-method calcGoodFeature 5 1 true

#SNAPFLAGS= -disjoint-debug-snap-method t3 5 20 true

BSFLAGS= -justanalyze -disjoint -disjoint-k 1 -disjoint-write-dots all -disjoint-alias-file aliases.txt normal -enable-assertions -mainclass ${MAINCLASS}

all:
	$(BUILDSCRIPT) $(BSFLAGS) $(DEBUGFLAGS) $(SNAPFLAGS) *.java

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
