MAINCLASS1=Drinker
MAINCLASS2=Simple
SRC1=${MAINCLASS1}.java
SRC2=${MAINCLASS2}.java
FLAGS1= -dsm -nooptimize -recovery -debug -mainclass ${MAINCLASS1}
FLAGS2= -dsm -nooptimize -recovery -debug -mainclass ${MAINCLASS2}
default:
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS1} ${SRC1}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS2} ${SRC2}
drinker:
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS1} ${SRC1}
simple:
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS2} ${SRC2}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
