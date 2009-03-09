MAINCLASS1=LookUpServerExample
MAINCLASS2=LookUpClient
SRC1=${MAINCLASS1}.java \
	 LookUpServerThread.java
SRC2=${MAINCLASS2}.java 
FLAGS= -thread -nooptimize -debug -mainclass ${MAINCLASS1}
FLAGS1= -thread -nooptimize -debug -mainclass ${MAINCLASS2}

default :
	../../../../buildscript ${FLAGS} -o Server ${SRC1}
	../../../../buildscript ${FLAGS1} -o Client ${SRC2}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
