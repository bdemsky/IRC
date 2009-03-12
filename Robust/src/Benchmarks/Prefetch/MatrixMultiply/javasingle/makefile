MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
SRC2=${MAINCLASS}Nrun.java
default:
	../../../../buildscript -thread -optimize -mainclass ${MAINCLASS} ${SRC1} -o ${MAINCLASS}
#	../../../../buildscript -thread -optimize -mainclass ${MAINCLASS} ${SRC2} -o ${MAINCLASS}N

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
