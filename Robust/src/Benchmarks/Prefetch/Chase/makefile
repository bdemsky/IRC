MAINCLASS=Chase
SRC1=${MAINCLASS}.java
FLAGS=-dsm -prefetch -optimize -debug -excprefetch Chase.main -excprefetch Chase.Chase -mainclass ${MAINCLASS} -trueprob 0.7
FLAGS2=-dsm -optimize -debug -mainclass ${MAINCLASS} -trueprob 0.7
#for testing no java option
#FLAGS=-dsm -prefetch -optimize -debug -excprefetch Chase.main -excprefetch Chase.Chase -mainclass ${MAINCLASS} -trueprob 0.99 -nojava
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NP ${SRC1}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}P ${SRC1}
	cp ${MAINCLASS}NP.bin ${MAINCLASS}1NP.bin
	cp ${MAINCLASS}P.bin ${MAINCLASS}1.bin

clean:
	rm *.bin
	rm -rf tmpbuilddirectory
