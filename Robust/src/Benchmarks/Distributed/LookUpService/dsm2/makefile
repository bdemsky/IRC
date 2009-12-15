MAINCLASS=LookUpService
SRC1=${MAINCLASS}.java \
     DistributedHashMap.java
FLAGS3=-dsm -transstats -optimize -mainclass ${MAINCLASS}
FLAGS4=-dsm -transstats -dsmcaching -optimize -mainclass ${MAINCLASS}
FLAGS5=-dsm -dsmcaching -transstats -prefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.95 -excprefetch DistributedHashMap.resize -excprefetch String.equals -excprefetch LookUpService.main -excprefetch String.hashCode
default:
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}NPNC ${SRC1}
	../../../../buildscript ${FLAGS4} -o ${MAINCLASS}NPC ${SRC1}
	../../../../buildscript ${FLAGS5} -o ${MAINCLASS}N ${SRC1}
clean:
	rm -rf tmpbuilddirectory
	rm *.bin
