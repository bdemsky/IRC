#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <pthread.h>
#include <sys/time.h>
#include <sys/poll.h>
#include <netdb.h>
#include <net/if.h>
#include <linux/sockios.h>
#include "dht.h"
#include "clookup.h" //this works for now, do we need anything better?

#define BUFFER_SIZE 512 //maximum message size
#define UDP_PORT 2157
#define TCP_PORT 2157
#define BACKLOG 10 //max pending tcp connections
#define TIMEOUT_MS 500
#define MAX_RETRIES 3
#define INIT_HOST_ALLOC 16
#define INIT_BLOCK_NUM 64
#define DEFAULT_INTERFACE "eth0"

enum {
	INSERT_COMMAND,
	REMOVE_COMMAND,
	SEARCH_COMMAND,
	FIND_LEADER_COMMAND,
	INSERT_RESPONSE,
	REMOVE_RESPONSE,
	SEARCH_RESPONSE,
	FIND_LEADER_RESPONSE
};


//status codes
enum {
	INSERT_OK,
	INSERT_ERROR,
	REMOVE_OK,
	REMOVE_ERROR,
	KEY_FOUND,
	KEY_NOT_FOUND,
	NOT_KEY_OWNER
};

struct hostData {
	unsigned int ipAddr;
	unsigned int maxKeyCapacity;
};

struct insertCmd {
	unsigned int msgType;
	unsigned int key;
	unsigned int val;
};

struct removeCmd {
	unsigned int msgType;
	unsigned int key;
};

struct searchCmd {
	unsigned int msgType;
	unsigned int key;
};

struct insertRes {
	unsigned int msgType;
	unsigned int status;
};

struct removeRes {
	unsigned int msgType;
	unsigned int status;
};

struct searchRes {
	unsigned int msgType;
	unsigned int status;
	unsigned int val;
};


//TODO: leave message, rebuild message...

struct hostData myHostData;
unsigned int numHosts;
struct hostData *hostArray;
unsigned int hostArraySize;
unsigned int numBlocks;
unsigned int *blockOwnerArray;
unsigned int blockOwnerArraySize;

unsigned int getMyIpAddr();
void *udpListen();
void *tcpListen();
void *tcpAccept(void *);
//returns number of bytes received in resBuffer, or -1 if an error occurred
int udpSendWaitForResponse(unsigned int dest_ip, unsigned short dest_port, void *msg, unsigned int msglen, void *resBuffer, unsigned int resBufferSize, unsigned int timeout, unsigned int numRetries);
int sendNoWait(unsigned int dest_ip, unsigned short dest_port, void *msg, unsigned int msglen);
unsigned int getKeyOwner(unsigned int key);
unsigned int hash(unsigned int x);

void dhtInit(unsigned int maxKeyCapacity)
{
	unsigned int myMessage;
	int bytesReceived;
	int i;

	myHostData.ipAddr = getMyIpAddr();
	myHostData.maxKeyCapacity = maxKeyCapacity;

	

	//announce presence (udp broadcast), get data structures from leader (leader initiates tcp transfer)
	

//if no response, I am the first

	numHosts = 1;
	hostArray = malloc(INIT_HOST_ALLOC * sizeof(struct hostData));
	hostArray[0] = myHostData;

	numBlocks = INIT_BLOCK_NUM;
	blockOwnerArray = malloc(numBlocks * sizeof(unsigned short));
	for (i = 0; i < numBlocks; i++)
	{
		blockOwnerArray[i] = 0;
	}
	
	//otherwise, scan array and choose blocks to take over
	//get data from hosts that own those blocks (tcp), fill hash table
	//notify (the leader or everybody?) of ownership changes
	
	//start server (udp)
	pthread_t threadUdpListen, threadTcpListen;
	pthread_create(&threadUdpListen, NULL, udpListen, NULL);
	pthread_create(&threadTcpListen, NULL, tcpListen, NULL);
	
	return;
}

void dhtExit()
{

}

int dhtInsert(unsigned int key, unsigned int val)
{
	unsigned int dest_ip = getKeyOwner(key);
	struct insertCmd myMessage;
	struct insertRes response;
	int bytesReceived;

	myMessage.msgType = INSERT_COMMAND;
	myMessage.key = key;
	myMessage.val = val;
	
	bytesReceived = udpSendWaitForResponse(dest_ip, UDP_PORT, (void *)&myMessage, sizeof(struct insertCmd), (void *)&response, sizeof(struct insertRes), TIMEOUT_MS, MAX_RETRIES);
	if (bytesReceived == sizeof(struct insertRes))
	{
		if (response.msgType == INSERT_RESPONSE)
		{
			if (response.status == INSERT_OK)
				return 0;
//			if (response.status == NOT_KEY_OWNER)
		}
	}
//TODO: find owner and try again, request rebuild if necessary
	return -1; //this function should be robust enough to always return 0
}

int dhtRemove(unsigned int key)
{
	unsigned int dest_ip = getKeyOwner(key);
	struct removeCmd myMessage;
	struct removeRes response;
	int bytesReceived;
	
	myMessage.msgType = REMOVE_COMMAND;
	myMessage.key = key;

	bytesReceived = udpSendWaitForResponse(dest_ip, UDP_PORT, (void *)&myMessage, sizeof(struct removeCmd), (void *)&response, sizeof(struct removeRes), TIMEOUT_MS, MAX_RETRIES);
	if (bytesReceived == sizeof(struct removeRes))
	{
		if (response.msgType == REMOVE_RESPONSE)
		{
			if (response.status == REMOVE_OK)
				return 0;
//			if (response.status == NOT_KEY_OWNER)
		}
	}
//TODO: find owner and try again, request rebuild if necessary
	return -1; //this function should be robust enough to always return 0
}

int dhtSearch(unsigned int key, unsigned int *val)
{
	unsigned int dest_ip = getKeyOwner(key);
	struct searchCmd myMessage;
	struct searchRes response;
	int bytesReceived;

	myMessage.msgType = SEARCH_COMMAND;
	myMessage.key = key;

	bytesReceived = udpSendWaitForResponse(dest_ip, UDP_PORT, (void *)&myMessage, sizeof(struct searchCmd), (void *)&response, sizeof(struct searchRes), TIMEOUT_MS, MAX_RETRIES);
	if (bytesReceived == sizeof(struct searchRes))
	{
		if (response.msgType == SEARCH_RESPONSE)
		{
			if (response.status == KEY_FOUND)
			{
				*val = response.val;
				return 0;
			}
			if (response.status == KEY_NOT_FOUND)
			{
				return 1;
			}
//			if (response.status == NOT_KEY_OWNER)
		}
	}
//TODO: find owner and try again, request rebuild if necessary
	return -1; //this function should be robust enough to always return 0 or 1
}



//use UDP for messages that are frequent and short
void *udpListen()
{
	struct sockaddr_in myAddr;
	struct sockaddr_in clientAddr;
	int sock;
	socklen_t socklen = sizeof(struct sockaddr_in);
	char buffer[BUFFER_SIZE];
	ssize_t bytesReceived;
	struct insertCmd *insertCmdPtr;
	struct removeCmd *removeCmdPtr;
	struct searchCmd *searchCmdPtr;
	struct insertRes *insertResPtr;
	struct removeRes *removeResPtr;
	struct searchRes *searchResPtr;
	char replyBuffer[BUFFER_SIZE];
	struct timeval now;

	chashtable_t *myHashTable = chashCreate(HASH_SIZE, LOADFACTOR);

	if ((sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
	{
		perror("socket()");
		pthread_exit(NULL);
	}
	
	bzero(&myAddr, socklen);
	myAddr.sin_family=AF_INET;
	myAddr.sin_addr.s_addr=INADDR_ANY;
	myAddr.sin_port=htons(UDP_PORT);

	if (bind(sock, (struct sockaddr *)&myAddr, socklen) == -1)
	{
		perror("bind()");
		pthread_exit(NULL);
	}
//	printf("listening...\n");
	while(1)
	{
		if ((bytesReceived = recvfrom(sock, buffer, BUFFER_SIZE, 0, (struct sockaddr *)&clientAddr, &socklen)) == -1)
		{
			perror("recvfrom()");
			break;
		}
		if (bytesReceived == 0)
		{
			printf("recvfrom() returned 0\n");
			break;
		}
		gettimeofday(&now, NULL);
//		printf("message received:%ds,%dus\n", now.tv_sec, now.tv_usec);

//		printf("Received %d bytes from %x:%d\n", bytesReceived, clientAddr.sin_addr.s_addr, clientAddr.sin_port);
		switch (buffer[0])
		{
			case INSERT_COMMAND:
				if (bytesReceived != sizeof(struct insertCmd))
				{
					printf("error: incorrect message size\n");
					break;
				}
				insertCmdPtr = (struct insertCmd *)buffer;
//				printf("Insert: key=%d, val=%d\n", insertCmdPtr->key, insertCmdPtr->val);
				insertResPtr = (struct insertRes *)replyBuffer;
				insertResPtr->msgType = INSERT_RESPONSE;
				if (getKeyOwner(insertCmdPtr->key) == myHostData.ipAddr)
				{
					//note: casting val to void * in order to conform to API
					if(chashInsert(myHashTable, insertCmdPtr->key, (void *)insertCmdPtr->val) == 0)
						insertResPtr->status = INSERT_OK;
					else
						insertResPtr->status = INSERT_ERROR;
				}
				else
				{
					insertResPtr->status = NOT_KEY_OWNER;;
				}
				sendto(sock, (void *)insertResPtr, sizeof(struct insertRes), 0, (struct sockaddr *)&clientAddr, socklen);
				break;
			case REMOVE_COMMAND:
				if (bytesReceived != sizeof(struct removeCmd))
				{
					printf("error: incorrect message size\n");
					break;
				}
				removeCmdPtr = (struct removeCmd *)buffer;
//				printf("Remove: key=%d\n", removeCmdPtr->key);
				removeResPtr = (struct removeRes *)replyBuffer;
				removeResPtr->msgType = REMOVE_RESPONSE;
				if (getKeyOwner(removeCmdPtr->key) == myHostData.ipAddr)
				{
					//note: casting val to void * in order to conform to API
					if(chashRemove(myHashTable, removeCmdPtr->key) == 0)
						removeResPtr->status = INSERT_OK;
					else
						removeResPtr->status = INSERT_ERROR;
				}
				else
				{
					removeResPtr->status = NOT_KEY_OWNER;
				}
				sendto(sock, (void *)removeResPtr, sizeof(struct removeRes), 0, (struct sockaddr *)&clientAddr, socklen);
				break;
			case SEARCH_COMMAND:
				if (bytesReceived != sizeof(struct searchCmd))
				{
					printf("error: incorrect message size\n");
					break;
				}
				searchCmdPtr = (struct searchCmd *)buffer;
//				printf("Search: key=%d\n",searchCmdPtr->key);
				searchResPtr = (struct searchRes *)replyBuffer;
				searchResPtr->msgType = SEARCH_RESPONSE;
				if (getKeyOwner(searchCmdPtr->key) == myHostData.ipAddr)
				{
					//note: casting val to void * in order to conform to API
					if((searchResPtr->val = (unsigned int)chashSearch(myHashTable, searchCmdPtr->key)) == 0)
						searchResPtr->status = KEY_NOT_FOUND;
					else
						searchResPtr->status = KEY_FOUND;
				}
				else
				{
					searchResPtr->status = NOT_KEY_OWNER;
				}
				sendto(sock, (void *)searchResPtr, sizeof(struct searchRes), 0, (struct sockaddr *)&clientAddr, socklen);
				break;
				//just ignore anything else
//			default:
//				printf("Unknown message type\n");
		}
	}
}

int udpSendWaitForResponse(unsigned int dest_ip, unsigned short dest_port, void *msg, unsigned int msglen, void *resBuffer, unsigned int resBufferSize, unsigned int timeout, unsigned int numRetries)
{
	struct sockaddr_in server_addr;
	struct sockaddr_in ack_addr;
	socklen_t socklen = sizeof(struct sockaddr_in);
	struct pollfd pollsock;
//	struct timeval now;
	int retval;
	int i;
	ssize_t bytesReceived;

	bzero((char *) &server_addr, sizeof(server_addr));
	server_addr.sin_family = AF_INET;
	server_addr.sin_port = htons(dest_port);
	server_addr.sin_addr.s_addr = htonl(dest_ip);

	if ((pollsock.fd = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
	{
		perror("socket()");
		return -1;
	}
	
	pollsock.events = POLLIN;
	
	for (i = 0; i < MAX_RETRIES; i++)
	{
//		if (i > 0)
//			printf("trying again, count: %d\n", i+1);
		if (sendto(pollsock.fd, msg, msglen, 0, (struct sockaddr *)&server_addr, socklen) == -1)
		{
			perror("sendto");
			return -1;
		}
//		gettimeofday(&now, NULL);
//		printf("message sent:%ds,%dus\n", now.tv_sec, now.tv_usec);
		retval = poll(&pollsock, 1, timeout);
		if (retval !=0)
		{
			bytesReceived = recvfrom(pollsock.fd, resBuffer, resBufferSize, 0, (struct sockaddr *)&ack_addr, &socklen);
			if ((ack_addr.sin_addr.s_addr == server_addr.sin_addr.s_addr)
			&& (ack_addr.sin_port == server_addr.sin_port))
			{
				close(pollsock.fd);
//				gettimeofday(&now, NULL);
//				printf("received response:%ds,%dus\n", now.tv_sec, now.tv_usec);
				return bytesReceived;
			}
		}
	}
	close(pollsock.fd);
//	gettimeofday(&now, NULL);
//	printf("timed out, no ack:%ds,%dus\n", now.tv_sec, now.tv_usec);
	return -1;
}

// use TCP for potentially large and/or important data transfer
void *tcpListen()
{
	struct sockaddr_in myAddr;
	struct sockaddr_in clientAddr;
	int sockListen, sockAccept;
	socklen_t socklen = sizeof(struct sockaddr_in);
	pthread_t threadTcpAccept;

	sockListen = socket(AF_INET, SOCK_STREAM, 0);
	if (sockListen == -1)
	{
		perror("socket()");
		pthread_exit(NULL);
	}

	myAddr.sin_family = AF_INET;
	myAddr.sin_port = htons(TCP_PORT);
	myAddr.sin_addr.s_addr = INADDR_ANY;
	memset(&(myAddr.sin_zero), '\0', 8);

	if (bind(sockListen, (struct sockaddr *)&myAddr, socklen) == -1)
	{
		perror("socket()");
		pthread_exit(NULL);
	}

	if (listen(sockListen, BACKLOG) == -1)
	{
		perror("listen()");
		pthread_exit(NULL);
	}

	while(1)
	{
		sockAccept = accept(sockListen, (struct sockaddr *)&clientAddr, &socklen);
		pthread_create(&threadTcpAccept, NULL, tcpAccept, (void *)sockAccept);
	}
}

void *tcpAccept(void *arg)
{
	int sockAccept = (int)arg;
	
	printf("accepted tcp connection, file descriptor: %d\n", sockAccept);

	sleep(30);

	if (close(sockAccept) == -1)
	{
		perror("close()");
	}

	printf("closed tcp connection, file descriptor: %d\n", sockAccept);

	pthread_exit(NULL);
}

unsigned int getKeyOwner(unsigned int key)
{
	return hostArray[blockOwnerArray[hash(key)]].ipAddr;
}

unsigned int getMyIpAddr()
{	
	int sock;
	struct ifreq interfaceInfo;
	struct sockaddr_in *myAddr = (struct sockaddr_in *)&interfaceInfo.ifr_addr;

	memset(&interfaceInfo, 0, sizeof(struct ifreq));

	if((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0)
	{
		perror("socket()");
		return 1;
	}

	strcpy(interfaceInfo.ifr_name, DEFAULT_INTERFACE);
	myAddr->sin_family = AF_INET;
	
	if(ioctl(sock, SIOCGIFADDR, &interfaceInfo) != 0)
	{
		perror("ioctl()");
		return 1;
	}

	return ntohl(myAddr->sin_addr.s_addr);
}

unsigned int hash(unsigned int x)
{
	return x % numBlocks;
}

