MAINCLASS=SingleObjectMod
SRC=${MAINCLASS}.java \
    ../KMeans/Random.java \
    ../../../ClassLibrary/intwrapper.java

FLAGSSTATS=-stmstats -mainclass ${MAINCLASS} -singleTM -optimize -debug -joptimize -64bit -abcclose -dcopts -transstats -arraypad 
FLAGS=-mainclass ${MAINCLASS} -singleTM -nooptimize -debug -joptimize -64bit -abcclose -dcopts -transstats -arraypad 

base:
	../../../buildscript ${FLAGS} ${SRC} -o STATS${MAINCLASS}NoLockBase

stmlock:
	../../../buildscript ${FLAGSSTATS} -abrt 10 ${SRC} -o STATS${MAINCLASS}10
	../../../buildscript ${FLAGSSTATS} -abrt 20 ${SRC} -o STATS${MAINCLASS}20
	../../../buildscript ${FLAGSSTATS} -abrt 30 ${SRC} -o STATS${MAINCLASS}30
	../../../buildscript ${FLAGSSTATS} -abrt 40 ${SRC} -o STATS${MAINCLASS}40
	../../../buildscript ${FLAGSSTATS} -abrt 45 ${SRC} -o STATS${MAINCLASS}45
	../../../buildscript ${FLAGSSTATS} -abrt 50 ${SRC} -o STATS${MAINCLASS}50
	../../../buildscript ${FLAGSSTATS} -abrt 55 ${SRC} -o STATS${MAINCLASS}55
	../../../buildscript ${FLAGSSTATS} -abrt 60 ${SRC} -o STATS${MAINCLASS}60
	../../../buildscript ${FLAGSSTATS} -abrt 65 ${SRC} -o STATS${MAINCLASS}65
	../../../buildscript ${FLAGSSTATS} -abrt 70 ${SRC} -o STATS${MAINCLASS}70
	../../../buildscript ${FLAGSSTATS} -abrt 80 ${SRC} -o STATS${MAINCLASS}80
	../../../buildscript ${FLAGSSTATS} -abrt 90 ${SRC} -o STATS${MAINCLASS}90
	../../../buildscript ${FLAGSSTATS} -abrt 100 ${SRC} -o STATS${MAINCLASS}100


clean:
	rm -rf tmpbuilddirectory 
	rm *.bin
