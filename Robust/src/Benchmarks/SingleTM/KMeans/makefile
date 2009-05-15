MAINCLASS=KMeans
SRC=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java \
    ../../../ClassLibrary/JavaSTM/Barrier.java
FLAGS=-mainclass ${MAINCLASS} -singleTM -optimize -debug -dcopts -joptimize -stmstats -fastmemcpy
FLAGS2=-mainclass ${MAINCLASS} -singleTM -optimize -debug -joptimize -dcopts -fastmemcpy -abcclose
FLAGS3=-mainclass ${MAINCLASS} -optimize -debug -joptimize -thread


default:
	../../../buildscript ${FLAGS2} -o GEN${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
