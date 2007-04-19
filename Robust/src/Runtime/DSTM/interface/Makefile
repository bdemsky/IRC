client:
	gcc -lpthread -g -o client trans.c testclient.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c

server:
	gcc -lpthread -g -o server dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c
all:
	gcc -lpthread -g -o client trans.c testclient.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c
	gcc -lpthread -g -o server dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c

clean:
	rm client server
