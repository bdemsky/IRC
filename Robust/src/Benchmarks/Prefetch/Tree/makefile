MAINCLASS=TreeT
SRC1=Tree.java
FLAGS=-dsm -prefetch -dsmcaching -optimize -excprefetch Tree.Populate -excprefetch Node.Inorder -excprefetch Node.Postorder -excprefetch TreeT.run -excprefetch TreeT.main -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS1=-dsm -dsmcaching -optimize -mainclass ${MAINCLASS} -trueprob 0.98
FLAGS2=-dsm -optimize -mainclass ${MAINCLASS}
default:
	../../../buildscript ${FLAGS2} -o ${MAINCLASS}NPNC ${SRC1}
#	../../../buildscript ${FLAGS1} -o ${MAINCLASS}NPC ${SRC1}
	../../../buildscript ${FLAGS} -o ${MAINCLASS}N ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
