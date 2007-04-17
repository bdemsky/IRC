#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <pthread.h>
#include <sys/time.h>
#include <sys/poll.h>
#include "dht.h"
#include "llookup.h"

#define BUFFER_SIZE 512
#define PORT 2157
#define TIMEOUT_MS 500

unsigned int numHosts;
struct hostData *hostList;
unsigned int numBlocks;
struct hostData *blockOwner;

void *dhtListen();
int sendWaitForAck(unsigned int dest_ip, unsigned short dest_port, void *msg, unsigned int msglen);

void dhtInit()
{
	
	//start server (udp)
	pthread_t threadListen;
	pthread_create(&threadListen, NULL, dhtListen, NULL);

	//announce presence (udp), get data structures from leader (leader initiates tcp transfer)
	//if no response, I am the first
	//otherwise, scan array and choose blocks to take over
	//get data from hosts that own those blocks (tcp), fill hash table
	//notify (the leader or everybody?) of ownership changes
	return;
}

void dhtExit()
{

}

int dhtInsert(unsigned int key, unsigned int val)
{
	unsigned int dest_ip = 0x7F000001;
	unsigned short dest_port = PORT;
	struct dhtInsertMsg myMessage;
	myMessage.msgType = DHT_INSERT;
	myMessage.key = key;
	myMessage.val = val;
	return sendWaitForAck(dest_ip, dest_port, (void *)&myMessage, sizeof(struct dhtInsertMsg));
}

int dhtRemove(unsigned int key)
{
	unsigned int dest_ip = 0x7F000001;
	unsigned short dest_port = PORT;
	struct dhtRemoveMsg myMessage;
	myMessage.msgType = DHT_REMOVE;
	myMessage.key = key;
	return sendWaitForAck(dest_ip, dest_port, (void *)&myMessage, sizeof(struct dhtRemoveMsg));
}

int dhtSearch(unsigned int key)
{
	unsigned int dest_ip = 0x7F000001;
	unsigned short dest_port = PORT;
	struct dhtSearchMsg myMessage;
	myMessage.msgType = DHT_SEARCH;
	myMessage.key = key;
	//TODO:this obviously requires more than an ACK, first implement actual hash table
	return sendWaitForAck(dest_ip, dest_port, (void *)&myMessage, sizeof(struct dhtSearchMsg));
}

//helper functions
void *dhtListen()
{
	struct sockaddr_in my_addr;
	struct sockaddr_in client_addr;
	int sock;
	socklen_t socklen = sizeof(struct sockaddr_in);
	char buffer[BUFFER_SIZE];
	ssize_t bytesReceived;
	struct timeval now;

	if ((sock = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
	{
		perror("socket()");
		exit(1);
	}
	
	bzero(&my_addr, socklen);
	my_addr.sin_family=AF_INET;
	my_addr.sin_addr.s_addr=INADDR_ANY;
	my_addr.sin_port=htons(PORT);

	if (bind(sock, (struct sockaddr *)&my_addr, socklen) == -1)
	{
		perror("bind()");
		exit(1);
	}
	printf("listening...\n");
	while(1)
	{
		if ((bytesReceived = recvfrom(sock, buffer, BUFFER_SIZE, 0, (struct sockaddr *)&client_addr, &socklen)) == -1)
		{
			printf("recvfrom() returned -1\n");
			break;
		}
		if (bytesReceived == 0)
		{
			printf("recvfrom() returned 0\n");
			break;
		}
		gettimeofday(&now, NULL);
		printf("message received:%ds,%dus\n", now.tv_sec, now.tv_usec);

		printf("Received %d bytes from %x:%d\n", bytesReceived, client_addr.sin_addr.s_addr, client_addr.sin_port);
		switch (buffer[0])
		{
			case DHT_INSERT:
				if (bytesReceived != sizeof(struct dhtInsertMsg))
				{
					printf("error: incorrect message size\n");
					break;
				}
				printf("Insert: key=%d, val=%d\n",((struct dhtInsertMsg *)buffer)->key,((struct dhtInsertMsg *)buffer)->val);
				buffer[0] = DHT_ACK;
				sendto(sock, buffer, 1, 0, (struct sockaddr *)&client_addr, socklen);
				break;
			case DHT_REMOVE:
				if (bytesReceived != sizeof(struct dhtRemoveMsg))
				{
					printf("error: incorrect message size\n");
					break;
				}
				printf("Remove: key=%d\n",((struct dhtRemoveMsg *)buffer)->key);
				buffer[0] = DHT_ACK;
				sendto(sock, buffer, 1, 0, (struct sockaddr *)&client_addr, socklen);
				break;
			case DHT_SEARCH:
				if (bytesReceived != sizeof(struct dhtSearchMsg))
				{
					printf("error: incorrect message size\n");
					break;
				}
				printf("Search: key=%d\n",((struct dhtSearchMsg *)buffer)->key);
				buffer[0] = DHT_ACK;
				sendto(sock, buffer, 1, 0, (struct sockaddr *)&client_addr, socklen);
				break;			
			default:
				printf("Unknown message type\n");
		}
	}
}

//send message, wait for response, resend twice before return failure
int sendWaitForAck(unsigned int dest_ip, unsigned  short dest_port, void *msg, unsigned int msglen)
{
	struct sockaddr_in server_addr;
	struct sockaddr_in ack_addr;
	socklen_t socklen = sizeof(struct sockaddr_in);
	struct pollfd pollsock;
	struct timeval now;
	int retval;
	int i;
	char ackByte;
	ssize_t bytesReceived;

	bzero((char *) &server_addr, sizeof(server_addr));
	server_addr.sin_family = AF_INET;
	server_addr.sin_port = htons(dest_port);
	server_addr.sin_addr.s_addr = htonl(dest_ip);

	if ((pollsock.fd = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
	{
		printf("error creating socket\n");
		return 1;
	}
	
	pollsock.events = POLLIN;
	
	for (i = 0; i < 3; i++)
	{
		if (i > 0)
			printf("trying again, count: %d\n", i+1);
		if (sendto(pollsock.fd, msg, msglen, 0, (struct sockaddr *)&server_addr, socklen) == -1)
		{
			printf("error sending\n");
			return 1;
		}
		gettimeofday(&now, NULL);
		printf("message sent:%ds,%dus\n", now.tv_sec, now.tv_usec);
		retval = poll(&pollsock, 1, TIMEOUT_MS);
		if (retval !=0)
		{
			bytesReceived = recvfrom(pollsock.fd, &ackByte, 1, 0, (struct sockaddr *)&ack_addr, &socklen);
			if ((bytesReceived == 1) && (ack_addr.sin_addr.s_addr == server_addr.sin_addr.s_addr)
			&& (ack_addr.sin_port == server_addr.sin_port) && (ackByte == DHT_ACK))
			{
				close(pollsock.fd);
				gettimeofday(&now, NULL);
				printf("received ack:%ds,%dus\n", now.tv_sec, now.tv_usec);
				return 0;
			}
		}
	}
	close(pollsock.fd);
	gettimeofday(&now, NULL);
	printf("timed out, no ack:%ds,%dus\n", now.tv_sec, now.tv_usec);
	return 1;
}

