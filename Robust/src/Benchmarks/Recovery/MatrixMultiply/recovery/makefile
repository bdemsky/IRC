MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}.java \
     GlobalQueue.java \
     TaskSet.java \
     Task.java \
     Worker.java \
     ../../../../ClassLibrary/JavaDSM/RecoveryStat.java

FLAGS=-recovery -recoverystats -transstats -dsm -dsmtask -prefetch -dsmcaching -32bit -debug -optimize -mainclass ${MAINCLASS} -excprefetch Task.isTodoListEmpty -excprefetch MatrixMultiply.output -excprefetch GlobalQueue.push -excprefetch MatrixMultiply.fillTodoList -excprefetch GlobalQueue.pop -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -excprefetch Work.checkCurrentWorkList -excprefetch MMul.getSum -excprefetch Task.grabTask -excprefetch Worker.Worker -excprefetch Task.dequeueTask -trueprob 0.96
DSMFLAGS= -dsm -dsmtask -transstats -dsmcaching -32bit -optimize -mainclass ${MAINCLASS} -excprefetch Task.isTodoListEmpty -excprefetch MatrixMultiply.output -excprefetch GlobalQueue.push -excprefetch MatrixMultiply.fillTodoList -excprefetch GlobalQueue.pop -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -excprefetch Work.checkCurrentWorkList -excprefetch MMul.getSum -excprefetch Task.grabTask
RECOVERYFLAGS=-recovery -dsm -dsmtask -nooptimize -debug -mainclass ${MAINCLASS} 

default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}
#	../../../../buildscript ${DSMFLAGS} -o ${MAINCLASS}DSM ${SRC1}
#	../../../../buildscript ${RECOVERYFLAGS} -o ${MAINCLASS} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
