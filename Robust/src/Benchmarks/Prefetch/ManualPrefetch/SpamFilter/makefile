MAINCLASS=SpamFilter
SRC=${MAINCLASS}.java \
     DistributedHashMap.java \
     Mail.java \
     FilterResult.java \
     HashEntry.java \
     HashStat.java \
     SignatureComputer.java \
     FilterStatistic.java \
     EphemeralSignature.java \
     GString.java \
     WhiplashSignature.java

FLAGS1=-dsm -optimize -builddir tmpbuilddirectory3 -mainclass ${MAINCLASS}
FLAGS2=-dsm -dsmcaching -builddir tmpbuilddirectory1 -optimize -mainclass ${MAINCLASS}
FLAGS3=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.98

FLAGSNPNC=-dsm -optimize -mainclass ${MAINCLASS}
FLAGSNPC=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS}
FLAGSP=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS} -trueprob 0.98

default:
	../../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPNC ${SRC}
	../../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPC ${SRC}
	../../../../buildscript ${FLAGS3} -o ${MAINCLASS}RangeN ${SRC}
#	../../../buildscript ${FLAGSNPNC} -o ${MAINCLASS}NPNC ${SRC}
#	../../../buildscript ${FLAGSNPC} -o ${MAINCLASS}NPC ${SRC}
#	../../../../buildscript ${FLAGSP} -o ${MAINCLASS}RangeN ${SRC}

clean:
	rm -rf tmpbuilddirectory*
	rm *.bin
