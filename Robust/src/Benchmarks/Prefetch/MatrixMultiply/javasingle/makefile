MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}N.java
SRC2=${MAINCLASS}Nrun.java
SRC3=${MAINCLASS}D.java
default:
	../../../../buildscript -thread -optimize -mainclass ${MAINCLASS} ${SRC3} -o ${MAINCLASS}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
