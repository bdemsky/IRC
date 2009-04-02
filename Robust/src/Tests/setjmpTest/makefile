all: sj

sj: sj.o Queue.o
	gcc -g sj.o Queue.o -o sj

Queue.o: Queue.c Queue.h
	gcc -g -c Queue.c -o Queue.o

sj.o: sj.c Queue.h
	gcc -g -c sj.c -o sj.o

clean:
	rm -f sj
	rm -f *.o
	rm -f *~
