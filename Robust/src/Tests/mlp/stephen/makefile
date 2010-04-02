PROGRAM=test

SOURCE_FILES=test.java

BUILDSCRIPT=../../../buildscript

USEMLP=  -mlp 8 2 -mlpdebug  # use to turn mlp on and off and make sure rest of build not broken
BSFLAGS= -32bit -nooptimize -debug -garbagestats -mainclass Test 
OWNERSHIP= -ownership -ownallocdepth 1 -enable-assertions  -methodeffects -flatirusermethods -ownwritedots final -ownaliasfile aliases.txt 

default:
	../../../buildscript -nojava $(USEMLP) $(BSFLAGS) $(OWNERSHIP) -o $(PROGRAM) $(SOURCE_FILES)

single:
	../../../buildscript $(BSFLAGS) -o $(PROGRAM) $(SOURCE_FILES)

java:
	../../../buildscript $(USEMLP) $(BSFLAGS) $(OWNERSHIP) -o $(PROGRAM) $(SOURCE_FILES)

clean:
	rm -f  $(PROGRAM).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt