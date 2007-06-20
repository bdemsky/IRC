d-3:
	gcc -dr -lpthread -g -o d-3 trans.c testd-3.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c

demsky:
	gcc -DDEBUG -lpthread -g -o demsky dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c

d-4:
	gcc -lpthread -g -o d-4 trans.c testd-4.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c

all:
	gcc -lpthread -g -o d-3 trans.c testd-3.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c
	gcc -lpthread -g -o demsky dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c
	gcc -lpthread -g -o d-4 trans.c testd-4.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c


mac:
	gcc -DMAC -lpthread -g -o d-3 trans.c testd-3.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c
	gcc -DMAC -lpthread -g -o demsky dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c
	gcc -DMAC -lpthread -g -o d-4 trans.c testd-4.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c

clean:
	rm -rf d-3 d-4 demsky
