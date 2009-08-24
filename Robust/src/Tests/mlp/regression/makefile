PROGRAM1=testSingle
PROGRAM2=testMulti

SOURCE_FILES=test.java

BUILDSCRIPT=~/research/Robust/src/buildscript

USEMLP= -mlp 8 2 -mlpdebug # use to turn mlp on and off and make sure rest of build not broken
BSFLAGS= -nooptimize -debug -garbagestats -mainclass Test -ownership -ownallocdepth 1 -enable-assertions -ownaliasfile aliases.txt

all: $(PROGRAM1).bin $(PROGRAM2).bin

$(PROGRAM1).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(USEMLP) $(BSFLAGS) -o $(PROGRAM1) $(SOURCE_FILES)

$(PROGRAM2).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT)           $(BSFLAGS) -o $(PROGRAM2) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM1).bin
	rm -f  $(PROGRAM2).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  aliases.txt
