MAINCLASS=KMeans
SRC=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java \
    ../../../ClassLibrary/JavaDSM/Barrier.java

FLAGSSTATS=-mainclass ${MAINCLASS} -dsm -optimize -debug -transstats

dsm:
	../../../buildscript ${FLAGSSTATS} -o ${MAINCLASS} ${SRC}

prep:

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
