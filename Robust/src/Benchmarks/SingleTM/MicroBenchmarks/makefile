MAINCLASS=SingleObjectMod
SRC=${MAINCLASS}.java \
    ../KMeans/Random.java \
    ../../../ClassLibrary/intwrapper.java

FLAGSSTATS=-stmstats -mainclass ${MAINCLASS} -singleTM -optimize -debug -joptimize -64bit -abcclose -dcopts -transstats -arraypad

stmlock:
	../../../buildscript ${FLAGSSTATS} ${SRC} -o STATS${MAINCLASS}

clean:
	rm -rf tmpbuilddirectory 
	rm *.bin
