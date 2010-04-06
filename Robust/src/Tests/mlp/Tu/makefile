PROGRAM=test

PROGRAM1=testSingle
PROGRAM2=testMulti

SOURCE_FILES=test.java

BUILDSCRIPT=../../../buildscript

#USE64BIT= -64bit
USEMLP= -mlp 8 2 -mlpdebug  # use to turn mlp on and off and make sure rest of build not broken
BSFLAGS= -32bit -nooptimize -debug -garbagestats -mainclass test 
OWNERSHIP= -ownership -ownallocdepth 1 -enable-assertions  -methodeffects -flatirusermethods -ownwritedots final -ownaliasfile aliases.txt 

default:
	../../../buildscript -nojava $(USEMLP) $(USE64BIT) $(BSFLAGS) $(OWNERSHIP) -o $(PROGRAM) $(SOURCE_FILES)

single:
	../../../buildscript $(BSFLAGS) -o $(PROGRAM) $(SOURCE_FILES)

java:
	../../../buildscript $(USEMLP) $(BSFLAGS) $(OWNERSHIP) -o $(PROGRAM) $(SOURCE_FILES)

both32bit: $(PROGRAM1).bin $(PROGRAM2).bin


$(PROGRAM1).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT)           $(BSFLAGS) -o $(PROGRAM1) $(SOURCE_FILES)
	rm -fr tmpbuilddirectory

$(PROGRAM2).bin: $(SOURCE_FILES)
	$(BUILDSCRIPT) $(USEMLP) $(BSFLAGS) -o $(PROGRAM2) $(SOURCE_FILES)



clean:
	rm -f  $(PROGRAM).bin
	rm -f  $(PROGRAM1).bin
	rm -f  $(PROGRAM2).bin
	rm -fr tmpbuilddirectory
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
