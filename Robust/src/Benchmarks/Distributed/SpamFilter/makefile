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

SRC2=${MAINCLASS}2.java \
     DistributedHashMap2.java \
     Mail.java \
     FilterResult.java \
     HashEntry.java \
     HashStat.java \
     SignatureComputer.java \
     FilterStatistic.java \
     EphemeralSignature.java \
     GString.java \
     WhiplashSignature.java

FLAGS1=-dsm -transstats -optimize -builddir tmpbuilddirectory3 -mainclass ${MAINCLASS}
FLAGS2=-dsm -dsmcaching -builddir tmpbuilddirectory1 -transstats -optimize -mainclass ${MAINCLASS}
FLAGS3=-dsm -dsmcaching -prefetch -builddir tmpbuilddirectory2 -transstats -optimize -mainclass ${MAINCLASS} -excprefetch HashStat.setuser -excprefetch String.hashCode -excprefetch String.equals -excprefetch HashStat.HashStat -excprefetch GString.toLocalCharArray -excprefetch SpamFilter.main -excprefetch HashEntry.getEngine -excprefetch HashEntry.getSignature -excprefetch HashStat.setuserid -excprefetch HashEntry.hashCode -excprefetch HashEntry.equals -excprefetch GString.GString -excprefetch SpamFilter.sendFeedBack -excprefetch HashStat.incSpamCount -excprefetch HashStat.incHamCount -trueprob 0.98

FLAGSNPNC=-dsm -optimize -mainclass ${MAINCLASS}
FLAGSNPC=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS}
FLAGSP=-dsm -dsmcaching -prefetch -optimize -mainclass ${MAINCLASS} -excprefetch HashStat.setuser -excprefetch String.hashCode -excprefetch String.equals -excprefetch HashStat.HashStat -excprefetch GString.toLocalCharArray -excprefetch SpamFilter.main -excprefetch HashEntry.getEngine -excprefetch HashEntry.getSignature -excprefetch HashStat.setuserid -excprefetch HashEntry.hashCode -excprefetch HashEntry.equals -excprefetch GString.GString -excprefetch SpamFilter.sendFeedBack -excprefetch HashStat.incSpamCount -excprefetch HashStat.incHamCount -trueprob 0.98

default:
	../../../buildscript ${FLAGS1} -o ${MAINCLASS}withstatNPNC ${SRC2}
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}withstatNPC ${SRC2}
	../../../buildscript ${FLAGS3} -o ${MAINCLASS}withstatN ${SRC2}
	../../../buildscript ${FLAGSNPNC} -o ${MAINCLASS}NPNC ${SRC2}
	../../../buildscript ${FLAGSNPC} -o ${MAINCLASS}NPC ${SRC2}
	../../../buildscript ${FLAGSP} -o ${MAINCLASS}N ${SRC2}

clean:
	rm -rf tmpbuilddirectory*
	rm *.bin
