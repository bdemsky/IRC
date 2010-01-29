PROGRAM1=testSingle
PROGRAM2=testMulti

SOURCE_FILES=test.java

BUILDSCRIPT=../../../buildscript

USEMLP= -mlp 8 2 -mlpdebug -methodeffects -ownership -ownallocdepth 1 -ownaliasfile aliases.txt
BSFLAGS= -nooptimize -debug -garbagestats -mainclass Test -enable-assertions

all: $(PROGRAM1).bin $(PROGRAM2).bin

$(PROGRAM1).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT)           $(BSFLAGS) -o $(PROGRAM1) $(SOURCE_FILES)
	rm -fr tmpbuilddirectory

$(PROGRAM2).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(USEMLP) $(BSFLAGS) -o $(PROGRAM2) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM1).bin
	rm -f  $(PROGRAM2).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  MethodEffects*txt
	rm -f  results*txt
