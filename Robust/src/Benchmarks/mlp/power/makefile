#raytracer
PROGRAM=test

SOURCE_FILES=Power.java

BUILDSCRIPT=~/eclipse/workspaces/irvine_sep09/Robust/src/buildscript

USEMLP=  -mlp 8 2  -mlpdebug  # use to turn mlp on and off and make sure rest of build not broken
BSFLAGS=   -32bit -nooptimize -mainclass Power -debug -garbagestats 
OWNERSHIP= -ownership -ownallocdepth 1 -enable-assertions  -methodeffects -flatirusermethods -ownwritedots final -ownaliasfile aliases.txt 

default:
	../../../buildscript -nojava $(USEMLP) $(BSFLAGS) $(OWNERSHIP) -o $(PROGRAM)p $(SOURCE_FILES) -builddir par

single:
	../../../buildscript $(BSFLAGS) -o $(PROGRAM)s $(SOURCE_FILES) -builddir sing

java:
	../../../buildscript $(USEMLP) $(BSFLAGS) $(OWNERSHIP) -o $(PROGRAM)p $(SOURCE_FILES) -builddir par

clean:
	rm -f  $(PROGRAM).bin
	rm -fr par sing
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
