MAINCLASS=Spider
SUBCLASS=Query
SRC1=${MAINCLASS}.java
SRC2=Global${SUBCLASS}.java
SRC3=${SUBCLASS}Thread.java
FLAGS= -dsm -transstats -nooptimize -debug -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC2} ${SRC3} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
