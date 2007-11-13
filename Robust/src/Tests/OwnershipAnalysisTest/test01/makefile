PROGRAM=test01

SOURCE_FILES=test01.java

BUILDSCRIPT=~/research/Robust/src/buildscript


all: view

view: PNGs
	eog *flatIRGraph*.png &
	eog *FN*.png &

PNGs: DOTs
	rm -f graph*.dot
	d2p *.dot

DOTs: $(PROGRAM).bin

$(PROGRAM).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) -recover -flatirtasks -ownership -o $(PROGRAM) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
