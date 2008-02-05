PROGRAM=test01

SOURCE_FILES=test01.java

BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -recover -flatirtasks -ownership -enable-assertions

all: view

view: PNGs
	eog *flatIRGraph*.png &
	eog *FN*.png &
	eog *Ownership*.png &

PNGs: DOTs
	rm -f *Startup*.dot
	rm -f *FN*Method*.dot
	rm -f *FN*Op*.dot
	rm -f *FN*Field*.dot
	rm -f *FN*SetField*.dot
	d2p *.dot

DOTs: $(PROGRAM).bin

$(PROGRAM).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(BSFLAGS) -o $(PROGRAM) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
