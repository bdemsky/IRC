MAINCLASS=Genome
SRC=${MAINCLASS}.java \
	Random.java \
  Bitmap.java \
	Gene.java \
  Segments.java \
  endInfoEntry.java \
  constructEntry.java \
  ../../../../ClassLibrary/JavaSTM/Barrier.java \
  Sequencer.java \
  Table.java
FLAGS=-mainclass ${MAINCLASS} -thread -nooptimize -debug -dcopts -joptimize

default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
