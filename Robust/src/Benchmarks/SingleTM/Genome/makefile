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
FLAGS=-mainclass ${MAINCLASS} -singleTM -optimize -debug -fastmemcpy -abcclose -dcopts -joptimize

default:
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
