MAINCLASS=fft2d
SRC=${MAINCLASS}.java \
	fft1d.java \
	Matrix.java
default:
	../../../../buildscript -thread -optimize -mainclass ${MAINCLASS} ${SRC} -o ${MAINCLASS}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
