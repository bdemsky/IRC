PROGRAM1=testMemPool-calloc
PROGRAM2=testMemPool-malloc
PROGRAM3=testMemPool-poolalloc

FLAGS=-O3

all: $(PROGRAM1) $(PROGRAM2) $(PROGRAM3)


$(PROGRAM1): $(PROGRAM1).c
	gcc $(FLAGS) $(PROGRAM1).c -lpthread -o $(PROGRAM1)

$(PROGRAM2): $(PROGRAM2).c
	gcc $(FLAGS) $(PROGRAM2).c -lpthread -o $(PROGRAM2)

$(PROGRAM3): $(PROGRAM3).c
	gcc $(FLAGS) $(PROGRAM3).c -lpthread -o $(PROGRAM3)


clean:
	rm -f $(PROGRAM1)
	rm -f $(PROGRAM2)
	rm -f $(PROGRAM3)
	rm -f *~
