MAINCLASS=KMeans
SRC=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java \
    ../../../ClassLibrary/JavaSTM/Barrier.java
FLAGS=-mainclass ${MAINCLASS} -singleTM -optimize -debug -dcopts -joptimize -transstats -stmstats
FLAGS2=-mainclass ${MAINCLASS} -singleTM -optimize -debug -dcopts -joptimize -transstats
FLAGS3=-mainclass ${MAINCLASS} -optimize -debug -joptimize -thread


default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
	../../../buildscript ${FLAGS3} -o LOCK${MAINCLASS} ${SRC}
	../../../buildscript ${FLAGS2} -o GEN${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
