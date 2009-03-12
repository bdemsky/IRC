MAINCLASS=Array
SRC1=${MAINCLASS}.java
FLAGS=-dsm -rangeprefetch -dsmcaching -optimize -excprefetch Array.Array -mainclass ${MAINCLASS} -trueprob 0.98
default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}RangeN ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
