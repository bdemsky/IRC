MAINCLASS=fft2d
SRC=${MAINCLASS}.java \
	fft1d.java \
	Matrix.java
SRC1=${MAINCLASS}rect.java \
	fft1d.java \
	Matrix1.java
SRC2=${MAINCLASS}N.java \
	fft1dN.java \
	MatrixN.java
default:
	../../../../buildscript -thread -optimize -mainclass ${MAINCLASS} ${SRC2} -o ${MAINCLASS}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
