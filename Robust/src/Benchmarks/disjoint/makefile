BUILDSCRIPT=~/research/Robust/src/buildscript

BSFLAGS= -recover -justanalyze -disjoint -disjoint-k 1 -disjoint-write-dots final -disjoint-alias-file aliases.txt normal -enable-assertions

all:
	$(BUILDSCRIPT) $(BSFLAGS) *.java

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
