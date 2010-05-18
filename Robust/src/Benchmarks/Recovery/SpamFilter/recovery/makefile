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

FLAGS=-recovery -recoverystats -dsm -dsmcaching -prefetch -transstats -32bit -debug -optimize -mainclass ${MAINCLASS} -excprefetch HashStat.setuser -excprefetch String.hashCode -excprefetch String.equals -excprefetch HashStat.HashStat -excprefetch GString.toLocalCharArray -excprefetch SpamFilter.main -excprefetch HashEntry.getEngine -excprefetch HashEntry.getSignature -excprefetch HashStat.setuserid -excprefetch HashEntry.hashCode -excprefetch HashEntry.equals -excprefetch GString.GString -excprefetch SpamFilter.sendFeedBack -excprefetch HashStat.incSpamCount -excprefetch HashStat.incHamCount -trueprob 0.98
DSMFLAGS=-dsm -dsmcaching -prefetch -transstats -32bit -optimize -mainclass ${MAINCLASS} -excprefetch HashStat.setuser -excprefetch String.hashCode -excprefetch String.equals -excprefetch HashStat.HashStat -excprefetch GString.toLocalCharArray -excprefetch SpamFilter.main -excprefetch HashEntry.getEngine -excprefetch HashEntry.getSignature -excprefetch HashStat.setuserid -excprefetch HashEntry.hashCode -excprefetch HashEntry.equals -excprefetch GString.GString -excprefetch SpamFilter.sendFeedBack -excprefetch HashStat.incSpamCount -excprefetch HashStat.incHamCount -trueprob 0.98

default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}
#	../../../../buildscript ${DSMFLAGS} -o ${MAINCLASS}DSM ${SRC}

clean:
	rm -rf tmpbuilddirectory*
	rm *.bin
