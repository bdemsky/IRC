MAINCLASS=KMeans
SRC=${MAINCLASS}.java \
	Random.java \
	Cluster.java \
    Normal.java \
    Common.java \
    GlobalArgs.java 

FLAGS=-mainclass ${MAINCLASS} -thread -optimize

default:
	../../../../buildscript ${FLAGS} -o ${MAINCLASS} ${SRC}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
