MAINCLASS=yada
SRC=${MAINCLASS}.java \
	tmpheap.java \
	Random.java  \
	tmpQueue_t.java  \
	coordinate.java  \
	edge.java	\
	element.java  \
	List_Node.java  \
	List_t.java \
	mesh.java  \
	region.java  \
	Vector_t.java \
	../../../ClassLibrary/JavaSTM/Barrier.java 

include ../common/Makefile.flags
include ../common/Makefile.builds

prep:
	cpp -P heap.java > tmpheap.java
	cpp -P Queue_t.java > tmpQueue_t.java

clean:
	rm tmp*.java
	rm ttt*.java
	rm -rf tmpbuilddirectory


