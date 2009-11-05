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

FLAGS1=-dsm -optimize -mainclass ${MAINCLASS}
FLAGS2=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS}
FLAGS3=-dsm -dsmcaching -rangeprefetch -optimize -mainclass ${MAINCLASS}

default:
	../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPNC ${SRC}
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPC ${SRC}
	../../../buildscript ${FLAGS3} -o ${MAINCLASS}RangeN ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
