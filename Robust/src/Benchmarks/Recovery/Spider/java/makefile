MAINCLASS=Spider
SUBCLASS=Query
SRC1=${MAINCLASS}.java
SRC2=Local${SUBCLASS}.java
SRC3=${SUBCLASS}Task.java
FLAGS= -32bit -nooptimize -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC2} ${SRC3} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
