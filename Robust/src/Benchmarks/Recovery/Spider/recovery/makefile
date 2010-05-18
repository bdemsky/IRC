MAINCLASS=Spider
SUBCLASS=Query
SRC1=${MAINCLASS}.java
SRC2=Global${SUBCLASS}.java
SRC3=${SUBCLASS}Task.java
FLAGS=-recoverystats -recovery -transstats -dsmcaching -32bit -dsm -dsmtask -optimize -mainclass ${MAINCLASS}
DSMFLAGS=-dsm -dsmtask -transstats -dsmcaching -debug -optimize -mainclass ${MAINCLASS}
default:
#	../../../../buildscript ${DSMFLAGS} -o ${MAINCLASS}DSM GlobalQuery.java LocalQuery.java QueryTask.java Spider.java Task.java GlobalQueue.java Worker.java TaskSet.java
#	../../../../buildscript ${FLAGS} -o ${MAINCLASS} GlobalQuery.java LocalQuery.java QueryTask.java Spider.java Task.java GlobalQueue.java Worker.java TaskSet.java DistributedHashMap.java
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} GlobalQuery.java LocalQuery.java QueryTask.java Spider.java Task.java GlobalQueue.java Worker.java TaskSet.java

clean:
	rm -rf tmpbuilddirectory
	rm *.bin

