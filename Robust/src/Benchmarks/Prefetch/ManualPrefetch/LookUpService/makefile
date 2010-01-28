MAINCLASS=LookUpService
SRC1=${MAINCLASS}.java \
     DistributedHashMap.java
FLAGS=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.95
FLAGS2=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.95
FLAGS3=-dsm -optimize -mainclass ${MAINCLASS} -trueprob 0.95

default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}RangeN ${SRC1}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPC ${SRC1}
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}NPNC ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
