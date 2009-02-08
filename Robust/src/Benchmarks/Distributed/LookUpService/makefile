MAINCLASS=LookUpService
SRC1=${MAINCLASS}.java \
     ../../../ClassLibrary/DistributedHashMap.java
FLAGS1=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.90
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
default:
#	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC1}
	../../../buildscript ${FLAGS1} -o ${MAINCLASS}RangePN ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
