MAINCLASS=KMeans
SRC=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java \
    ../../../../ClassLibrary/JavaDSM/Barrier.java

FLAGS=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.98 -transstats

default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}RangeN ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
