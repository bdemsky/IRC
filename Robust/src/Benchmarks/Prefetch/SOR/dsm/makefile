MAINCLASS=JGFSORBenchSizeD
SRC=${MAINCLASS}.java \
	JGFSORBench.java \
	JGFInstrumentor.java \
	JGFTimer.java \
	SORRunner.java \
	SORWrap.java \
    ../../../../ClassLibrary/JavaDSM/Barrier.java

FLAGS=-dsm -transstats -prefetch -dsmcaching -builddir tmpbuilddirectory2 -optimize -excprefetch JGFSORBench.JGFSORBench -excprefetch JGFSORBenchSizeA.main -excprefetch JGFSORBench.RandomMatrix -excprefetch JGFSORBench.init_sync -excprefetch JGFSORBench.JGFkernel -trueprob 0.72
FLAGS1=-dsm -dsmcaching -transstats -optimize -builddir tmpbuilddirectory1 -mainclass ${MAINCLASS} 
FLAGS2=-dsm -optimize -transstats

FLAGSP=-dsm -prefetch -dsmcaching -optimize -builddir tmpbuilddirectory2 -excprefetch JGFSORBench.JGFSORBench -excprefetch JGFSORBenchSizeA.main -excprefetch JGFSORBench.RandomMatrix -excprefetch JGFSORBench.init_sync -excprefetch JGFSORBench.JGFkernel -trueprob 0.72
FLAGSNPC=-dsm -dsmcaching -optimize -builddir tmpbuilddirectory1 -mainclass ${MAINCLASS} 
FLAGSNPNC=-dsm -optimize 

default:
	../../../../buildscript ${FLAGSNPNC} -mainclass ${MAINCLASS} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGSNPC} -mainclass ${MAINCLASS} -o ${MAINCLASS}NPC ${SRC}
	../../../../buildscript ${FLAGSP} -mainclass ${MAINCLASS} -o ${MAINCLASS}N  ${SRC}
	../../../../buildscript ${FLAGS2} -mainclass ${MAINCLASS} -o ${MAINCLASS}withstatNPNC ${SRC}
	../../../../buildscript ${FLAGS} -mainclass ${MAINCLASS} -o ${MAINCLASS}withstatN  ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
