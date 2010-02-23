MAINCLASS=RainForest
SRC=tmp${MAINCLASS}.java \
	Player.java \
	TreeType.java \
	GameMap.java \
	RockType.java \
	Goal.java \
	Path.java \
	Node.java \
	AStarPathFinder.java 
    
FLAGS1=-optimize -thread -mainclass ${MAINCLASS} 

default:
	cpp ${MAINCLASS}.java > tmp1${MAINCLASS}.java
	./extractLines
	../../../../buildscript ${FLAGS1} -o Game ${SRC}

clean:
	rm tmp1RainForest.java
	rm tmpRainForest.java
	rm -rf tmpbuilddirectory
	rm *.bin
