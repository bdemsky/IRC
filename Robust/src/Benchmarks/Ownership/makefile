BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -recover -ownership -ownaliasfile aliases.txt -enable-assertions
AD3= -ownallocdepth 3
AD5= -ownallocdepth 5
AD9= -ownallocdepth 9

all: ad3


ad3:
	$(BUILDSCRIPT) $(BSFLAGS) $(AD3) *.java

ad5:
	$(BUILDSCRIPT) $(BSFLAGS) $(AD5) *.java

ad9:
	$(BUILDSCRIPT) $(BSFLAGS) $(AD9) *.java


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
	rm -f  aliases.txt
