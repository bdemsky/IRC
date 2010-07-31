PROGRAM=Labyrinth

SOURCE_FILES=Coordinate.java CoordPathWrapper.java Grid.java Labyrinth.java List_Iter.java List_Node.java List_t.java Maze.java Pair.java Point.java Queue_Int.java Queue_t.java Router.java Solve_Arg.java Vector_t.java

SOURCE_FILES2=Coordinate.java CoordPathWrapper.java Grid.java Labyrinth.java List_Iter.java List_Node.java List_t.java Maze.java Pair.java Point.java Queue_Int.java Queue_t.java RouterSingle.java Solve_Arg.java Vector_t.java

BUILDSCRIPT=../../../buildscript

USEOOO= -ooojava 24 2  -ooodebug -joptimize
BSFLAGS= -64bit -mainclass $(PROGRAM) -garbagestats -joptimize -noloop -optimize
DISJOINT= -disjoint -disjoint-k 1 -enable-assertions

default:
	$(BUILDSCRIPT) -nojava $(USEOOO) $(BSFLAGS) $(DISJOINT) -o $(PROGRAM)p $(SOURCE_FILES) -builddir par

singler:
	$(BUILDSCRIPT) $(BSFLAGS) -o $(PROGRAM)s -builddir sing2 $(SOURCE_FILES2)

single:
	$(BUILDSCRIPT) $(BSFLAGS) -thread -o $(PROGRAM)s -builddir sing $(SOURCE_FILES) 

ooo:
	$(BUILDSCRIPT) $(USEOOO) $(BSFLAGS) $(DISJOINT) -o $(PROGRAM)p -builddir par $(SOURCE_FILES) 

disjoint:
	$(BUILDSCRIPT) -justanalyze $(BSFLAGS) $(DISJOINT) -o $(PROGRAM)p -builddir par $(SOURCE_FILES) 


clean:
	rm -f  $(PROGRAM)p.bin $(PROGRAM)s.bin 
	rm -fr par sing sing2
	rm -f  *~
	rm -f  *.dot
	rm -f  *.png
	rm -f  *.txt
	rm -f  aliases.txt
	rm -f  mlpReport*txt
	rm -f  results*txt
