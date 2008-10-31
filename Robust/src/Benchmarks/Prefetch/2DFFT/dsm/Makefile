MAINCLASS=fft2d
SRC=${MAINCLASS}.java \
	fft1d.java \
	Matrix.java
FLAGS = -dsm -optimize -mainclass ${MAINCLASS}

default:
	../../../../buildscript ${FLAGS} ${SRC} -o ${MAINCLASS}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
