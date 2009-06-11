QDIR=../../Runtime
DEFS= -D "RUNMALLOC=malloc" -D "RUNFREE=free" -D "DEBUG_QUEUE="

all: a.out

a.out: testMain.o queue.o
	gcc testMain.o queue.o

queue.o: $(QDIR)/Queue.h $(QDIR)/Queue.c
	gcc -c -I$(QDIR) $(DEFS) $(QDIR)/Queue.c -o queue.o

testMain.o: testMain.c $(QDIR)/Queue.h
	gcc -c -I$(QDIR) $(DEFS) testMain.c -o testMain.o

clean:
	rm -f a.out
	rm -f *.o
	rm -f *~
