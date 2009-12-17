MAINCLASS1=ReadArray
NAME1=OneMCReadcommit
MAINCLASS2=WriteArray
NAME2=OneMCWritecommit
MAINCLASS3=ReadArrayObj
NAME3=MultiMCReadcommit
MAINCLASS4=WriteArrayObj
NAME4=MultiMCWritecommit
MAINCLASS6=MoreSimple
NAME6=MoreSimple
SRC1=${NAME1}.java \
	ReadArrayWrap.java
SRC2=${NAME2}.java \
	WriteArrayWrap.java
SRC3=${NAME3}.java \
	ReadArrayObjWrap.java
SRC4=${NAME4}.java \
	WriteArrayObjWrap.java
SRC6=${NAME6}.java
MAINCLASS7=Chase3
SRC7=${MAINCLASS7}.java \
     DistributedHashMap.java

FLAGS1 = -dsm -prefetch -dsmcaching -nooptimize -debug -mainclass ${MAINCLASS1}
FLAGS2 = -dsm -dsmcaching -optimize -mainclass ${MAINCLASS1}
FLAGS3 = -dsm -prefetch -dsmcaching -nooptimize -debug -mainclass ${MAINCLASS2}
FLAGS4 = -dsm -dsmcaching -optimize -mainclass ${MAINCLASS2}
FLAGS5 = -dsm -prefetch -dsmcaching -nooptimize -debug -mainclass ${MAINCLASS3}
FLAGS6 = -dsm -dsmcaching -optimize -mainclass ${MAINCLASS3}
FLAGS7 = -dsm -prefetch -dsmcaching -nooptimize -debug -mainclass ${MAINCLASS4}
FLAGS8 = -dsm -dsmcaching -nooptimize -debug -mainclass ${MAINCLASS4}
FLAGS9 = -dsm -nooptimize -debug -mainclass ${MAINCLASS4}
FLAGS10= -dsm -nooptimize -recovery -debug -mainclass ${MAINCLASS5}
FLAGS11= -dsm -runtimedebug -nooptimize -recovery -debug -mainclass ${MAINCLASS6}
FLAGS12= -nojava -dsm -runtimedebug -nooptimize -recovery -debug -mainclass ${MAINCLASS5}
FLAGS13= -dsm -transstats -optimize -mainclass ${MAINCLASS7}
FLAGS14= -dsm -dsmcaching -transstats -optimize -mainclass ${MAINCLASS7}
FLAGS15= -dsm -dsmcaching -transstats -trueprob 0.95 -excprefetch Chase3.main -prefetch -nooptimize -mainclass ${MAINCLASS7}


default:
#	../../../buildscript ${FLAGS2} -o ${NAME1}NP ${SRC1}
#	cp ${NAME1}NP.bin ${NAME1}1NP.bin
#	cp ${NAME1}NP.bin ${NAME1}2NP.bin
#	cp ${NAME1}NP.bin ${NAME1}3NP.bin
#	cp ${NAME1}NP.bin ${NAME1}4NP.bin
#	cp ${NAME1}NP.bin ${NAME1}5NP.bin
#	cp ${NAME1}NP.bin ${NAME1}6NP.bin
#	cp ${NAME1}NP.bin ${NAME1}7NP.bin
#	cp ${NAME1}NP.bin ${NAME1}8NP.bin
#	../../../buildscript ${FLAGS4} -o ${NAME2}NP ${SRC2}
#	cp ${NAME2}NP.bin ${NAME2}1NP.bin
#	cp ${NAME2}NP.bin ${NAME2}2NP.bin
#	cp ${NAME2}NP.bin ${NAME2}3NP.bin
#	cp ${NAME2}NP.bin ${NAME2}4NP.bin
#	cp ${NAME2}NP.bin ${NAME2}5NP.bin
#	cp ${NAME2}NP.bin ${NAME2}6NP.bin
#	cp ${NAME2}NP.bin ${NAME2}7NP.bin
#	cp ${NAME2}NP.bin ${NAME2}8NP.bin
#	../../../buildscript ${FLAGS6} -o ${NAME3}NP ${SRC3}
#	cp ${NAME3}NP.bin ${NAME3}1NP.bin
#	cp ${NAME3}NP.bin ${NAME3}2NP.bin
#	cp ${NAME3}NP.bin ${NAME3}3NP.bin
#	cp ${NAME3}NP.bin ${NAME3}4NP.bin
#	cp ${NAME3}NP.bin ${NAME3}5NP.bin
#	cp ${NAME3}NP.bin ${NAME3}6NP.bin
#	cp ${NAME3}NP.bin ${NAME3}7NP.bin
#	cp ${NAME3}NP.bin ${NAME3}8NP.bin
#	cp ${NAME4}NP.bin ${NAME4}1NP.bin
#	../../../buildscript ${FLAGS9} -o ${NAME4}NPNC ${SRC4}
#	cp ${NAME4}NP.bin ${NAME4}2NP.bin
#	cp ${NAME4}NP.bin ${NAME4}3NP.bin
#	cp ${NAME4}NP.bin ${NAME4}4NP.bin
#	cp ${NAME4}NP.bin ${NAME4}5NP.bin
#	cp ${NAME4}NP.bin ${NAME4}6NP.bin
#	cp ${NAME4}NP.bin ${NAME4}7NP.bin
#	cp ${NAME4}NP.bin ${NAME4}8NP.bin
# 	../../../buildscript ${FLAGS11} -o ${NAME6}NPNC ${SRC6}

test:
	../../../make
	default

samplespam:
	../../../buildscript ${FLAGS13} -o ${MAINCLASS7}NPNC ${SRC7}
	../../../buildscript ${FLAGS14} -o ${MAINCLASS7}NPC ${SRC7}
	../../../buildscript ${FLAGS15} -o ${MAINCLASS7}N ${SRC7}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
