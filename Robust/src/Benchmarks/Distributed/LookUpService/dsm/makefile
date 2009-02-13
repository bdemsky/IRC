MAINCLASS=LookUpService
SRC1=${MAINCLASS}.java \
     DistributedHashMap.java
FLAGS1=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.95
FLAGS2=-dsm -dsmcaching -prefetch -optimize -excprefetch String.hashCode -excprefetch DistributedHashMap.resize -excprefetch String.equals -excprefetch LookUpService.main -mainclass ${MAINCLASS} -trueprob 0.95
FLAGS3=-dsm -optimize -mainclass ${MAINCLASS}
FLAGS4=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}NPNC ${SRC1}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}N ${SRC1}
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}RangeN ${SRC1}
	../../../../buildscript ${FLAGS4} -o ${MAINCLASS}NPC ${SRC1}
clean:
	rm -rf tmpbuilddirectory
	rm *.bin
