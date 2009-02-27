MAINCLASS=RainForest
SRC=tmp${MAINCLASS}.java \
	Player.java \
	TreeType.java \
	GameMap.java \
	RockType.java \
	Barrier.java

FLAGS1=-dsm -optimize -mainclass ${MAINCLASS}
FLAGS2=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS}
FLAGS3=-dsm -dsmcaching -prefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.90
FLAGS4=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.90

default:
	cpp ${MAINCLASS}.java > tmp1${MAINCLASS}.java
	./extractLines
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPNC ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
