PROGRAM=Labyrinth

SOURCE_FILES=Coordinate.java CoordPathWrapper.java Grid.java Labyrinth.java List_Iter.java List_Node.java List_t.java Maze.java Pair.java Point.java Queue_Int.java Queue_t.java Router.java Solve_Arg.java Vector_t.java

SOURCE_FILES2=Coordinate.java CoordPathWrapper.java Grid.java Labyrinth.java List_Iter.java List_Node.java List_t.java Maze.java Pair.java Point.java Queue_Int.java Queue_t.java RouterSingle.java Solve_Arg.java Vector_t.java

BUILDSCRIPT=../../../buildscript


COREPROFOVERFLOW= #-coreprof-checkoverflow
USECOREPROF= -coreprof $(COREPROFOVERFLOW) \
	-coreprof-eventwords 1024*1024*128 \
	-coreprof-enable cpe_main \
	-coreprof-enable cpe_taskdispatch
#	-coreprof-enable cpe_runmalloc \
#	-coreprof-enable cpe_runfree \
#	-coreprof-enable cpe_count_poolalloc \
#	-coreprof-enable cpe_count_poolreuse \
#	-coreprof-enable cpe_workschedgrab \
#	-coreprof-enable cpe_taskexecute \
#	-coreprof-enable cpe_taskretire \
#	-coreprof-enable cpe_taskstallvar \
#	-coreprof-enable cpe_taskstallmem


USEOOO= -ooojava 24 2  -ooodebug -ooodebug-disable-task-mem-pool 
BSFLAGS= -64bit -mainclass $(PROGRAM)  -heapsize-mb 1024 -garbagestats -debug -joptimize -noloop -optimize 

DRELEASEMODE=-disjoint-release-mode -disjoint-alias-file aliases.txt tabbed
DISJOINT= -disjoint -disjoint-k 1 -enable-assertions $(DRELEASEMODE) #-disjoint-desire-determinism

default:
	$(BUILDSCRIPT) -nojava $(BSFLAGS) $(USECOREPROF) $(USEOOO) $(DISJOINT) -o $(PROGRAM)p -builddir par  $(SOURCE_FILES)

singler:
	$(BUILDSCRIPT)         $(BSFLAGS)                                      -o $(PROGRAM)s -builddir sing2 $(SOURCE_FILES2)

single:
	$(BUILDSCRIPT) -thread $(BSFLAGS) $(USECOREPROF)                       -o $(PROGRAM)s -builddir sing $(SOURCE_FILES) 

ooo:
	$(BUILDSCRIPT)         $(BSFLAGS) $(USECOREPROF) $(USEOOO) $(DISJOINT) -o $(PROGRAM)p -builddir par  $(SOURCE_FILES) 

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
	rm -f coreprof.dat
	rm -f trace.out
