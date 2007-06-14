d-2:
	gcc -lpthread -g -o d-2 trans.c testclient.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c

demsky:
	gcc -DDEBUG -lpthread -g -o demksy dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c

d-1:
	gcc -lpthread -g -o d-1 trans.c testd-1.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c

all:
	gcc -lpthread -g -o d-2 trans.c testclient.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c
	gcc -lpthread -g -o demksy dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c
	gcc -lpthread -g -o d-1 trans.c testd-1.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c

clean:
	rm -rf d-2 d-1 demsky
