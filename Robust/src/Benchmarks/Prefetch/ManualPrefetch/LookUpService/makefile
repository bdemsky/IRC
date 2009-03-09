MAINCLASS=LookUpService
SRC1=${MAINCLASS}.java \
     DistributedHashMap.java
FLAGS=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.95
default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS}RangeN ${SRC1}
clean:
	rm -rf tmpbuilddirectory
	rm *.bin
