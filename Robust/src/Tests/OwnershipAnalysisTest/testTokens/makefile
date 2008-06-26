all: run

Main.class: Main.java
	javac -classpath ../../.. Main.java

run: Main.class
	java -classpath .:../../.. Main

clean:
	rm -f *~
	rm -f *.class
