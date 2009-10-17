MAINCLASS=yada
SRC=${MAINCLASS}.java \
	tmpheap.java \
	List_t.java \
	Random.java  \
	tmpQueue_t.java  \
	coordinate.java  \
	edge.java	\
	element.java  \
	List_Node.java  \
	mesh.java  \
	region.java  \
	Vector_t.java \
	tmpavltree.java \
	../../../ClassLibrary/JavaSTM/Barrier.java 

include ../common/Makefile.flags
include ../common/Makefile.builds

prep:
	cpp -P heap.java > tmpheap.java
	cpp -P avltree.java > tmpavltree.java
	cpp -P Queue_t.java > tmpQueue_t.java

clean:
	rm tmp*.java
	rm ttt*.java
	rm -rf tmpbuilddirectory


