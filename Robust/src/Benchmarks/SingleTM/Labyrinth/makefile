MAINCLASS=Labyrinth
SRC=ttttmp${MAINCLASS}.java \
	../common/Pair.java \
	../common/Queue_t.java \
	Vector_t.java \
	../common/List_t.java \
	../common/List_Node.java \
	../common/List_Iter.java \
	Coordinate.java \
	ttttmpGrid.java \
	Maze.java \
	ttttmpRouter.java \
	Point.java \
	Solve_arg.java \
	../../../ClassLibrary/JavaSTM/Barrier.java 

FLAGS=-mainclass ${MAINCLASS} -thread -nooptimize -debug

default:
	cpp ${MAINCLASS}.java > tmp${MAINCLASS}.java
	cpp Grid.java	> tmpGrid.java
	cpp Router.java > tmpRouter.java
	./extractLines tmp${MAINCLASS}.java
	./extractLines tmpGrid.java
	./extractLines tmpRouter.java
	../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
	rm ttt*.java tmp*.java

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
