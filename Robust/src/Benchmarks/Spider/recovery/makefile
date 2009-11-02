MAINCLASS=Spider
SUBCLASS=Query
SRC1=${MAINCLASS}.java
SRC2=Global${SUBCLASS}.java
SRC3=${SUBCLASS}Queue.java
SRC4=${SUBCLASS}Task.java
FLAGS= -recovery -dsmtask -dsm -dsmtask -32bit -nooptimize -debug -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC2} ${SRC3} ${SRC4} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
