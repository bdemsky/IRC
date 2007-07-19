d-3:
	gcc -dr -lpthread -g -o d-3 trans.c testd-3.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c

demsky:
	gcc -DDEBUG -lpthread -g -o demsky dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c queue.c prelookup.c 

d-4:
	gcc -lpthread -g -o d-4 trans.c testd-4.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c

all:
	gcc -lpthread -g -o d-3 trans.c testd-3.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c
	gcc -lpthread -g -o demsky dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c queue.c prelookup.c
	gcc -lpthread -g -o d-4 trans.c testd-4.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c


mac:
	gcc -DMAC -lpthread -g -o d-3 trans.c testd-3.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c
	gcc -DMAC -lpthread -g -o demsky dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c queue.c prelookup.c
	gcc -DMAC -lpthread -g -o d-4 trans.c testd-4.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c

clean:
	rm -rf d-3 d-4 demsky
