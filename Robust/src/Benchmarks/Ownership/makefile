BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -recover -justanalyze -ownership -ownallocdepth 1 -enable-assertions #-ownwritedots final #-flatirtasks


tabbed:
	$(BUILDSCRIPT) $(BSFLAGS) -ownaliasfiletab aliases.txt *.java

normal:
	$(BUILDSCRIPT) $(BSFLAGS) -ownaliasfile aliases.txt *.java


#view: PNGs
#	eog *.png &

#PNGs: DOTs
#	d2p *COMPLETE*.dot

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
