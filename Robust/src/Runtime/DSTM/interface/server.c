#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <netdb.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>

#define LISTEN_PORT 2153
#define BACKLOG 10 //max pending connections
#define RECIEVE_BUFFER_SIZE 1500

void *dstm_listen();
void *dstm_accept(void *);

int main()
{
	pthread_t thread_listen;

	pthread_create(&thread_listen, NULL, dstm_listen, NULL);	

	pthread_join(thread_listen, NULL);
	return 0;
}

void *dstm_listen()
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
		pthread_create(&thread_dstm_accept, NULL, dstm_accept, (void *)acceptfd);
	}
	pthread_exit(NULL);
}

void *dstm_accept(void *acceptfd)
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


	
