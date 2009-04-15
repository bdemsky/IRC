MAINCLASS=KMeans
SRC=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java \
    ../../../ClassLibrary/JavaSTM/Barrier.java
FLAGS=-mainclass ${MAINCLASS} -singleTM -nooptimize -debug -dcopts -abcclose -transstats -profile -joptimize
default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
