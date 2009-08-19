MAINCLASS1=ReadArray
NAME1=OneMCReadcommit
MAINCLASS2=WriteArray
NAME2=OneMCWritecommit
MAINCLASS3=ReadArrayObj
NAME3=MultiMCReadcommit
MAINCLASS4=WriteArrayObj
NAME4=MultiMCWritecommit
SRC1=${NAME1}.java \
	ReadArrayWrap.java
SRC2=${NAME2}.java \
	WriteArrayWrap.java
SRC3=${NAME3}.java \
	ReadArrayObjWrap.java \
	../../../ClassLibrary/JavaDSM/Barrier.java
SRC4=${NAME4}.java \
	WriteArrayObjWrap.java \
	../../../ClassLibrary/JavaDSM/Barrier.java
FLAGS1 = -dsm -prefetch -dsmcaching -optimize -debug -mainclass ${MAINCLASS1}
FLAGS2 = -dsm -dsmcaching -optimize -mainclass ${MAINCLASS1}
FLAGS3 = -dsm -prefetch -dsmcaching -optimize -debug -mainclass ${MAINCLASS2}
FLAGS4 = -dsm -dsmcaching -optimize -mainclass ${MAINCLASS2}
FLAGS5 = -dsm -prefetch -dsmcaching -optimize -debug -mainclass ${MAINCLASS3}
FLAGS6 = -dsm -dsmcaching -optimize -mainclass ${MAINCLASS3}
FLAGS7 = -dsm -prefetch -dsmcaching -optimize -mainclass ${MAINCLASS4}
FLAGS8 = -dsm -dsmcaching -optimize -mainclass ${MAINCLASS4}

default:
	../../../buildscript ${FLAGS2} -o ${NAME1}NP ${SRC1}
	cp ${NAME1}NP.bin ${NAME1}1NP.bin
	cp ${NAME1}NP.bin ${NAME1}2NP.bin
	cp ${NAME1}NP.bin ${NAME1}3NP.bin
	cp ${NAME1}NP.bin ${NAME1}4NP.bin
	cp ${NAME1}NP.bin ${NAME1}5NP.bin
	cp ${NAME1}NP.bin ${NAME1}6NP.bin
	cp ${NAME1}NP.bin ${NAME1}7NP.bin
	cp ${NAME1}NP.bin ${NAME1}8NP.bin
	../../../buildscript ${FLAGS4} -o ${NAME2}NP ${SRC2}
	cp ${NAME2}NP.bin ${NAME2}1NP.bin
	cp ${NAME2}NP.bin ${NAME2}2NP.bin
	cp ${NAME2}NP.bin ${NAME2}3NP.bin
	cp ${NAME2}NP.bin ${NAME2}4NP.bin
	cp ${NAME2}NP.bin ${NAME2}5NP.bin
	cp ${NAME2}NP.bin ${NAME2}6NP.bin
	cp ${NAME2}NP.bin ${NAME2}7NP.bin
	cp ${NAME2}NP.bin ${NAME2}8NP.bin
	../../../buildscript ${FLAGS6} -o ${NAME3}NP ${SRC3}
	cp ${NAME3}NP.bin ${NAME3}1NP.bin
	cp ${NAME3}NP.bin ${NAME3}2NP.bin
	cp ${NAME3}NP.bin ${NAME3}3NP.bin
	cp ${NAME3}NP.bin ${NAME3}4NP.bin
	cp ${NAME3}NP.bin ${NAME3}5NP.bin
	cp ${NAME3}NP.bin ${NAME3}6NP.bin
	cp ${NAME3}NP.bin ${NAME3}7NP.bin
	cp ${NAME3}NP.bin ${NAME3}8NP.bin
	../../../buildscript ${FLAGS8} -o ${NAME4}NP ${SRC4}
	cp ${NAME4}NP.bin ${NAME4}1NP.bin
	cp ${NAME4}NP.bin ${NAME4}2NP.bin
	cp ${NAME4}NP.bin ${NAME4}3NP.bin
	cp ${NAME4}NP.bin ${NAME4}4NP.bin
	cp ${NAME4}NP.bin ${NAME4}5NP.bin
	cp ${NAME4}NP.bin ${NAME4}6NP.bin
	cp ${NAME4}NP.bin ${NAME4}7NP.bin
	cp ${NAME4}NP.bin ${NAME4}8NP.bin

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
