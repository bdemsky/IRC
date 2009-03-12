MAINCLASS=Chase
SRC1=${MAINCLASS}.java
FLAGS1=-dsm -rangeprefetch -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.99
#for testing no java option
#FLAGS=-dsm -prefetch -optimize -debug -excprefetch Chase.main -excprefetch Chase.Chase -mainclass ${MAINCLASS} -trueprob 0.99 -nojava
default:
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}RangeN ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
