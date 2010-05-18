MAINCLASS=FileSystem
SRC1=${MAINCLASS}.java \
     Directory.java \
     DFile.java \
     DistributedHashMap.java \
     GlobalString.java \
     GlobalStringBuffer.java \
     DistributedLinkedList.java
FLAGS= -optimize -thread -mainclass ${MAINCLASS}
default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
