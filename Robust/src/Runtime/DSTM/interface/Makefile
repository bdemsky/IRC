dw-2:
	gcc -dr -lpthread -g -o dw-2 trans.c testdw-2.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c mcpileq.c machinepile.c

demsky:
	gcc -DDEBUG -lpthread -g -o demsky dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c queue.c prelookup.c mcpileq.c machinepile.c 

dw-1:
	gcc -lpthread -g -o dw-1 trans.c testdw-1.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c mcpileq.c machinepile.c

all:
	gcc -lpthread -g -o dw-2 trans.c testdw-2.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c mcpileq.c machinepile.c
	gcc -lpthread -g -o demsky dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c queue.c prelookup.c mcpileq.c machinepile.c
	gcc -lpthread -g -o dw-1 trans.c testdw-1.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c mcpileq.c machinepile.c


mac:
	gcc -DMAC -lpthread -g -o dw-2 trans.c testdw-2.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c mcpileq.c machinepile.c
	gcc -DMAC -lpthread -g -o demsky dstmserver.c testserver.c plookup.c mlookup.c clookup.c llookup.c dstm.c objstr.c trans.c ip.c queue.c prelookup.c mcpileq.c machinepile.c
	gcc -DMAC -lpthread -g -o dw-1 trans.c testdw-1.c mlookup.c clookup.c llookup.c dstm.c objstr.c dstmserver.c plookup.c ip.c queue.c prelookup.c mcpileq.c machinepile.c

clean:
	rm -rf dw-2 dw-1 demsky
