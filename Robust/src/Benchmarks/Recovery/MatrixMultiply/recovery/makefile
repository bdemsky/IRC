MAINCLASS=MatrixMultiply
SRC1=${MAINCLASS}.java \

FLAGS=-recovery -recoverystats -dsm -dsmtask -transstats -prefetch -dsmcaching -32bit -optimize -mainclass ${MAINCLASS} -excprefetch Task.isTodoListEmpty -excprefetch MatrixMultiply.output -excprefetch GlobalQueue.push -excprefetch MatrixMultiply.fillTodoList -excprefetch GlobalQueue.pop -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -excprefetch Work.checkCurrentWorkList -excprefetch MMul.getSum -excprefetch Task.grabTask
DSMFLAGS= -dsm -dsmtask -transstats -prefetch -dsmcaching -32bit -optimize -mainclass ${MAINCLASS} -excprefetch Task.isTodoListEmpty -excprefetch MatrixMultiply.output -excprefetch GlobalQueue.push -excprefetch MatrixMultiply.fillTodoList -excprefetch GlobalQueue.pop -excprefetch MatrixMultiply.main -excprefetch MMul.setValues -excprefetch MMul.transpose -excprefetch Work.checkCurrentWorkList -excprefetch MMul.getSum -excprefetch Task.grabTask
RECOVERYFLAGS=-recovery -dsm -dsmtask -nooptimize -debug -mainclass ${MAINCLASS} 

default:
#	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}
#	../../../../buildscript ${DSMFLAGS} -o ${MAINCLASS}DSM ${SRC1}
	../../../../buildscript ${RECOVERYFLAGS} -o ${MAINCLASS} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
