PROGRAM=test

SOURCE_FILES=$(PROGRAM).java

all:
	javac -Xlint:unchecked -classpath ../../.. $(SOURCE_FILES)

run:
	java -classpath .:../../.. $(PROGRAM)


clean:
	rm -f  *.class
	rm -f  *~
