PROGRAM=test01

SOURCE_FILES=test01.java

BUILDSCRIPT=~/research/Robust/src/buildscript
BSFLAGS= -recover -flatirtasks -ownership #-enable-assertions
#BSFLAGS= -recover -ownership -enable-assertions

all: $(PROGRAM).bin

view: PNGs
	eog *flatIRGraph*.png &
	#eog *FN*.png &
	#eog *Ownership*.png &
	eog *.png &

printable:
	rm -f *Startup*.dot
	rm -f *FlatMethod*.dot
	rm -f *FlatOpNode*.dot
	rm -f *FlatFieldNode*.dot
	rm -f *FlatSetFieldNode*.dot
	rm -f *FlatCall*.dot
	rm -f *Parameter*.dot
	rm -f *Penguin*.dot
	rm -f *Voo*.dot
	rm -f *Baw*.dot
	rm -f *COMPLETE.dot
	dot2eps *.dot

PNGs: DOTs
	#rm -f *Startup*.dot
	rm -f *FlatMethod*.dot
	rm -f *FlatOpNode*.dot
	rm -f *FlatFieldNode*.dot
	rm -f *FlatSetFieldNode*.dot
	rm -f *FlatCall*.dot
	rm -f *Parameter*.dot
	rm -f *Penguin*.dot
	rm -f *Voo*.dot
	rm -f *Baw*.dot
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
	rm -f  *.ps
	rm -f  *.eps
