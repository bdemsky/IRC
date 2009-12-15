MAINCLASS=LookUpService
SRC1=${MAINCLASS}.java \
     DistributedHashMap.java
FLAGS2=-dsm -transstats -dsmcaching -prefetch -optimize -excprefetch String.hashCode -excprefetch DistributedHashMap.resize -excprefetch String.equals -excprefetch LookUpService.main -mainclass ${MAINCLASS} -trueprob 0.95
FLAGS3=-dsm -transstats -optimize -mainclass ${MAINCLASS}
FLAGS4=-dsm -dsmcaching -transstats -debug -optimize -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}NPNC ${SRC1}
	../../../../buildscript ${FLAGS4} -o ${MAINCLASS}NPC ${SRC1}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}N ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
