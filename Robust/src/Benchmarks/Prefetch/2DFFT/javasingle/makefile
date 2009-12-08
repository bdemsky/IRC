MAINCLASS=fft2d
SRC=${MAINCLASS}.java \
	fft1d.java \
	Matrix.java
SRC1=${MAINCLASS}rect.java \
	fft1d.java \
	Matrix1.java
default:
#	../../../../buildscript -thread -optimize -mainclass ${MAINCLASS} ${SRC} -o ${MAINCLASS}
	../../../../buildscript -thread -optimize -mainclass ${MAINCLASS} ${SRC1} -o ${MAINCLASS}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
