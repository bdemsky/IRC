MAINCLASS=Labyrinth
SRC=ttttmp${MAINCLASS}.java \
	../common/Pair.java \
	ttttmpQueue_t.java \
	ttttmpQueue_Int.java \
	Vector_t.java \
	List_t.java \
	List_Node.java \
	List_Iter.java \
	Coordinate.java \
	ttttmpGrid.java \
	ttttmpMaze.java \
	ttttmpRouter.java \
	Point.java \
	Solve_arg.java \
	../../../ClassLibrary/JavaSTM/Barrier.java 

FLAGS=-mainclass ${MAINCLASS} -singleTM -optimize -debug -delaycomp -joptimize -fastmemcpy -profile -32bit -inlineatomic 2

FLAGS2=-mainclass ${MAINCLASS} -thread -optimize -debug -joptimize -32bit

FLAGS3=-mainclass ${MAINCLASS} -singleTM -nooptimize -debug -32bit

default:
	cpp ${MAINCLASS}.java > tmp${MAINCLASS}.java
	cpp Grid.java	> tmpGrid.java
	cpp Router.java > tmpRouter.java
	cpp Maze.java > tmpMaze.java
	cpp Queue_t.java > tmpQueue_t.java
	cpp Queue_Int.java > tmpQueue_Int.java
	./extractLines tmp${MAINCLASS}.java
	./extractLines tmpGrid.java
	./extractLines tmpRouter.java
	./extractLines tmpMaze.java
	./extractLines tmpQueue_t.java
	./extractLines tmpQueue_Int.java
#	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
	../../../buildscript ${FLAGS3} -o STM${MAINCLASS} ${SRC}
#	../../../buildscript ${FLAGS2} -o LOCK${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin

test:
	cpp ${MAINCLASS}.java > tmp${MAINCLASS}.java
	cpp Grid.java	> tmpGrid.java
	cpp Router.java > tmpRouter.java
	cpp Maze.java > tmpMaze.java
	cpp Queue_t.java > tmpQueue_t.java
	cpp Queue_Int.java > tmpQueue_Int.java
	./extractLines tmp${MAINCLASS}.java
	./extractLines tmpGrid.java
	./extractLines tmpRouter.java
	./extractLines tmpMaze.java
	./extractLines tmpQueue_t.java
	./extractLines tmpQueue_Int.java
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
	rm ttt*.java tmp*.java
	./Labyrinth.bin -i inputs/random-x32-y32-z3-n64.txt
