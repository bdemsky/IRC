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

FLAGS=-optimize -thread -mainclass ${MAINCLASS} 

default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory*
	rm *.bin
