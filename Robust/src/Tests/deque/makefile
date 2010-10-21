PROGRAM=test-deque

DQDIR=../../Runtime
DEFS= -D "RUNMALLOC=malloc" -D "RUNFREE=free" -DBIT64 -DDEBUG_DEQUE
FLAGS= -m64 -g #-O3

all: $(PROGRAM)

$(PROGRAM): $(PROGRAM).o deque.o
	gcc $(PROGRAM).o deque.o -lpthread -o $(PROGRAM)

deque.o: $(DQDIR)/deque.h $(DQDIR)/deque.c
	gcc -c $(FLAGS) -I$(DQDIR) $(DEFS) $(DQDIR)/deque.c -o deque.o

$(PROGRAM).o: $(PROGRAM).c $(DQDIR)/deque.h
	gcc -c $(FLAGS) -I$(DQDIR) $(DEFS) $(PROGRAM).c -o $(PROGRAM).o

clean:
	rm -f $(PROGRAM)
	rm -f *.o
	rm -f *~
