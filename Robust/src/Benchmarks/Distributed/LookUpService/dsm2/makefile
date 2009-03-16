MAINCLASS=LookUpService
SRC1=${MAINCLASS}.java \
     DistributedHashMap.java
FLAGS3=-dsm -optimize -mainclass ${MAINCLASS}
FLAGS4=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}NPNC ${SRC1}
	../../../../buildscript ${FLAGS4} -o ${MAINCLASS}NPC ${SRC1}
clean:
	rm -rf tmpbuilddirectory
	rm *.bin
