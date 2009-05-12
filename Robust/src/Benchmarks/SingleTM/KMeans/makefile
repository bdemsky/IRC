MAINCLASS=KMeans
SRC=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java \
    ../../../ClassLibrary/JavaSTM/Barrier.java
FLAGS=-mainclass ${MAINCLASS} -singleTM -optimize -debug -dcopts -transstats -joptimize
FLAGS2=-mainclass ${MAINCLASS} -optimize -debug -joptimize -thread


default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
	../../../buildscript ${FLAGS2} -o NON${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
