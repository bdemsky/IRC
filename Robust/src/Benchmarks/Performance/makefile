FLAGS=-optimize -recover

default:
	../../buildscript ${FLAGS} -o FractalBR Fractal.java
	../../buildscript ${FLAGS} -o Dispatch Dispatch.java
	gcc -O9 Fractal.c -o FractalC


clean:
	rm -rf tmpbuilddirectory
	rm *.bin
