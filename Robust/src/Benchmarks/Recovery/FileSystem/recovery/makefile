MAINCLASS=FileSystem
SRC1=${MAINCLASS}2.java \
     DistributedHashMap.java
FLAGS= -recovery -recoverystats -transstats -dsm -dsmcaching -debug -optimize -mainclass ${MAINCLASS}
DSMFLAGS=-transstats -dsm -dsmcaching -optimize -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}
#	../../../../buildscript ${DSMFLAGS} -o ${MAINCLASS}DSM ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
