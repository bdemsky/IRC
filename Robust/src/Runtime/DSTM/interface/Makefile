client:
	gcc -g -O0 -o client trans.c testclient.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c

server:
	gcc -g -O0 -o server dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c 

clean:
	rm client server
