MAINCLASS=KMeans
SRC=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java \
    ../../../ClassLibrary/JavaDSM/Barrier.java

FLAGS=-mainclass ${MAINCLASS} -dsm -optimize -transstats
FLAGS1=-mainclass ${MAINCLASS} -dsm -dsmcaching -optimize -transstats

dsm:
	../../../buildscript ${FLAGS} -o ${MAINCLASS}NPNC ${SRC}
	../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPC ${SRC}

prep:

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
