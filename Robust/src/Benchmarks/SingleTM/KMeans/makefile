MAINCLASS=KMeans
SRC=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java \
    ../../../ClassLibrary/JavaSTM/Barrier.java
FLAGS=-singleTM -nooptimize -mainclass ${MAINCLASS} -debug
default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
