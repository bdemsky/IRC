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

FLAGS1=-dsm -transstats -optimize -mainclass ${MAINCLASS}
FLAGS2=-dsm -transstats -dsmcaching -optimize -mainclass ${MAINCLASS}
FLAGS3=-dsm -dsmcaching -transstats -prefetch -optimize -mainclass ${MAINCLASS} -excprefetch HashStat.setuser -excprefetch HashStat.incHamCount -excprefetch String.hashCode -excprefetch String.equals -excprefetch HashStat.HashStat -excprefetch GString.toLocalCharArray -excprefetch SpamFilter.main -excprefetch HashEntry.getEngine -excprefetch HashEntry.getSignature -excprefetch HashStat.setuserid -excprefetch HashEntry.hashCode -excprefetch HashEntry.equals -excprefetch GString.GString -excprefetch HashStat.incSpamCount -trueprob 0.98

default:
	../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPNC ${SRC2}
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPC ${SRC2}
	../../../buildscript ${FLAGS3} -o ${MAINCLASS}N ${SRC2}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
