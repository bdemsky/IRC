MAINCLASS=Intruder
SRC=ttttmp${MAINCLASS}.java \
	ttttmpQueue_t.java \
	ttttmpStream.java  \
	ttttmpDecoder.java  \
	ttttmpRBTree.java  \
	Preprocessor.java \
	Detector.java	\
	convertURNHex.java  \
	toLower.java	\
	Decoded.java  \
	ERROR.java  \
	Dictionary.java \
	Node.java  \
	Packet.java  \
	Random.java  \
	Vector_t.java \
	List_Node.java \
	List_t.java \
	List_Iter.java \
	Arg.java \
	../../../ClassLibrary/JavaSTM/Barrier.java 


FLAGS=-nooptimize -debug -thread -mainclass ${MAINCLASS}

default:
	cpp ${MAINCLASS}.java > tmp${MAINCLASS}.java
	cpp Stream.java > tmpStream.java
	cpp Decoder.java > tmpDecoder.java
	cpp RBTree.java > tmpRBTree.java
	cpp Queue_t.java > tmpQueue_t.java
	./extractLines tmp${MAINCLASS}.java
	./extractLines tmpQueue_t.java
	./extractLines tmpStream.java
	./extractLines tmpDecoder.java
	./extractLines tmpRBTree.java
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm tmp*.java
	rm ttt*.java
	rm -rf tmpbuilddirectory
	rm *.bin

test:
	cpp ${MAINCLASS}.java > tmp${MAINCLASS}.java
	cpp Stream.java > tmpStream.java
	cpp Decoder.java > tmpDecoder.java
	cpp RBTree.java > tmpRBTree.java
	cpp Queue_t.java > tmpQueue_t.java
	./extractLines tmp${MAINCLASS}.java
	./extractLines tmpQueue_t.java
	./extractLines tmpStream.java
	./extractLines tmpDecoder.java
	./extractLines tmpRBTree.java
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
	./Intruder.bin -a 10 -l 4 -n 2038 -s 1


