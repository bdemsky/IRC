CLASSPATH=../../..
MAINCLASS=CanonicalTest

all: $(MAINCLASS).java
	javac -classpath $(CLASSPATH) -Xlint $(MAINCLASS).java

run:
	java -classpath $(CLASSPATH) Analysis.Disjoint.UnitTests.$(MAINCLASS)

clean:
	rm -f *.class
	rm -f *~
