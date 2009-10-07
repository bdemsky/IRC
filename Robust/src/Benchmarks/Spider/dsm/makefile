MAINCLASS=Spider
SUBCLASS=Query
SRC1=${MAINCLASS}.java
SRC2=${SUBCLASS}.java
SRC3=${SUBCLASS}Queue.java
SRC4=${SUBCLASS}Thread.java
FLAGS= -dsm -32bit -nooptimize -debug -recovery -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC2} ${SRC3} ${SRC4} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
	rm *.php
	rm *.css
	rm www*
	rm eee*
	rm web*
