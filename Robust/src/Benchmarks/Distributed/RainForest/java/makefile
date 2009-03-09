MAINCLASS1=RainForestServerExample
MAINCLASS2=RainForestClient
SRC=tmp${MAINCLASS1}.java \
	tmpRainForestServerThread.java \
	TreeType.java \
	GameMap.java \
	RockType.java \
	Player.java \
	BarrierNonDSM.java

SRC1=tmp${MAINCLASS2}.java \
	GameMap.java \
	TreeType.java \
	RockType.java \
	Goal.java \
	Path.java \
	Node.java \
	Player.java \
	AStarPathFinder.java \
	BarrierNonDSM.java

FLAGS1= -thread -optimize -mainclass ${MAINCLASS1}
FLAGS2= -thread -optimize -mainclass ${MAINCLASS2}

default:
	  cpp RainForestServerExample.java > tmp1RainForestServerExample.java
	  cpp RainForestServerThread.java > tmp1RainForestServerThread.java
	  cpp RainForestClient.java > tmp1RainForestClient.java
	  ./extractLines
	  ../../../../buildscript ${FLAGS1} -o Server ${SRC}
	  ../../../../buildscript ${FLAGS2} -o Client ${SRC1}

clean:
	rm -rf tmpbuilddirectory
	rm *.bin
	rm tmp1RainForestServerExample.java
	rm tmp1RainForestServerThread.java
	rm tmp1RainForestClient.java
	rm tmpRainForestServerExample.java
	rm tmpRainForestServerThread.java
	rm tmpRainForestClient.java
