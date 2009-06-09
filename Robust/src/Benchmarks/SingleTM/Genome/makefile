MAINCLASS=Genome
SRC=${MAINCLASS}.java \
	../common/Random.java \
	Bitmap.java \
	Gene.java \
	Segments.java \
	endInfoEntry.java \
	constructEntry.java \
	../../../ClassLibrary/JavaSTM/Barrier.java \
	Sequencer.java \
	Table.java \
	Hashtable.java
FLAGS=-mainclass ${MAINCLASS} -singleTM -optimize -dcopts -abcclose -fastmemcpy -joptimize

default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
