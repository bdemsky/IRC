CLASSPATH=../..
MAINCLASS=MultiViewMapTest

all: $(MAINCLASS).java
	javac -classpath $(CLASSPATH) -Xlint $(MAINCLASS).java

run:
	java -classpath $(CLASSPATH) Util.UnitTests.$(MAINCLASS)

clean:
	rm -f *.class
	rm -f *~
