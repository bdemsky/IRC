#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <netdb.h>
#include <fcntl.h>
#include "dstm.h"
#include "mlookup.h"
#include "llookup.h"

#define LISTEN_PORT 2153
#define BACKLOG 10 //max pending connections
#define RECIEVE_BUFFER_SIZE 1500


objstr_t *mainobjstore;
mainobjstore = objstrCreate(DEFAULT_OBJ_STORE_SIZE);	

int dstmInit(void)
{
	//todo:initialize main object store
	//do we want this to be a global variable, or provide
	//separate access funtions and hide the structure?

	if (mhashCreate(HASH_SIZE, LOADFACTOR))
		return 1; //failure
	
	if (lhashCreate(HASH_SIZE, LOADFACTOR))
		return 1; //failure
	
	pthread_t threadListen;
	pthread_create(&threadListen, NULL, dstmListen, NULL);
	
	return 0;
}

void *dstmListen()
{
	int listenfd, acceptfd;
	struct sockaddr_in my_addr;
	struct sockaddr_in client_addr;
	socklen_t addrlength = sizeof(struct sockaddr);
	pthread_t thread_dstm_accept;
	int i;

	listenfd = socket(PF_INET, SOCK_STREAM, 0);
	if (listenfd == -1)
	{
		perror("socket");
		exit(1);
	}

	my_addr.sin_family = AF_INET;
	my_addr.sin_port = htons(LISTEN_PORT);
	my_addr.sin_addr.s_addr = INADDR_ANY;
	memset(&(my_addr.sin_zero), '\0', 8);

	if (bind(listenfd, (struct sockaddr *)&my_addr, addrlength) == -1)
	{
		perror("bind");
		exit(1);
	}
	
	if (listen(listenfd, BACKLOG) == -1)
	{
		perror("listen");
		exit(1);
	}

	printf("Listening on port %d, fd = %d\n", LISTEN_PORT, listenfd);
	while(1)
	{
		acceptfd = accept(listenfd, (struct sockaddr *)&client_addr, &addrlength);
		pthread_create(&thread_dstm_accept, NULL, dstmAccept, (void *)acceptfd);
	}
	pthread_exit(NULL);
}

void *dstmAccept(void *acceptfd)
{
	int numbytes;
	char buffer[RECIEVE_BUFFER_SIZE];
	int fd_flags = fcntl((int)acceptfd, F_GETFD);
	printf("Recieved connection: fd = %d\n", (int)acceptfd);
	do
	{
		numbytes = recv((int)acceptfd, (void *)buffer, sizeof(buffer), 0);
		buffer[numbytes] = '\0';
		if (numbytes == -1)
		{
			perror("recv");
			pthread_exit(NULL);
		}
		else
		{
			printf("Read %d bytes from %d\n", numbytes, (int)acceptfd);
			printf("%s", buffer);
		}
		
	} while (numbytes != 0);
	if (close((int)acceptfd) == -1)
	{
		perror("close");
		pthread_exit(NULL);
	}
	else
		printf("Closed connection: fd = %d\n", (int)acceptfd);
	pthread_exit(NULL);
}


