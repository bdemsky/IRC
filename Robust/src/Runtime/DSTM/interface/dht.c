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
#define LISTEN_PORT 2157
#define TIMEOUT_MS 500
#define MAX_RETRIES 3
#define INIT_NUM_BLOCKS 16
#define DEFAULT_INTERFACE "eth0"

//general commands
#define INSERT_COMMAND 1
#define REMOVE_COMMAND 2
#define SEARCH_COMMAND 3
//general responses
#define INSERT_RESPONSE 4
#define REMOVE_RESPONSE 5
#define SEARCH_RESPONSE 6

//#define JOIN
//#define LEAVE
//reserved for leader
//#define REBUILD

//etc...

//status codes
#define INSERT_OK 1
#define INSERT_ERROR 2
#define REMOVE_OK 3
#define REMOVE_ERROR 4
#define KEY_FOUND 5
#define KEY_NOT_FOUND 6
#define NOT_KEY_OWNER 7

struct hostData {
	unsigned int ipAddr;
	unsigned int maxKeyCapacity;
	struct hostData *next;
};

struct insertCmd {
	unsigned char msgType;
	unsigned int unused:12;
	unsigned int key;
	unsigned int val;
};

struct removeCmd {
	unsigned char msgType;
	unsigned int unused:12;
	unsigned int key;
};

struct searchCmd {
	unsigned char msgType;
	unsigned int unused:12;
	unsigned int key;
};

struct insertRes {
	unsigned char msgType;
	unsigned int status:12;
};

struct removeRes {
	unsigned char msgType;
	unsigned int status:12;
};

struct searchRes {
	unsigned char msgType;
	unsigned int status:12;
	unsigned int val;
};

/*struct joinMsg {
	unsigned char msgType;
	unsigned int unused:12;
	struct hostData newHost;
};*/

//TODO: leave message, rebuild message...

unsigned int numHosts;
struct hostData *hostList;
struct hostData *myHostData;
unsigned int numBlocks;
struct hostData **blockOwner;


unsigned int getMyIpAddr();
void *dhtListen();
//returns number of bytes received in resBuffer, or -1 if an error occurred
int sendWaitForResponse(unsigned int dest_ip, unsigned short dest_port, void *msg, unsigned int msglen, void *resBuffer, unsigned int resBufferSize, unsigned int timeout, unsigned int numRetries);
int sendNoWait(unsigned int dest_ip, unsigned short dest_port, void *msg, unsigned int msglen);
unsigned int getKeyOwner(unsigned int key);
unsigned int hash(unsigned int x);

void dhtInit(unsigned int maxKeyCapacity)
{
	int i;

	myHostData = malloc(sizeof(struct hostData));
	myHostData->ipAddr = getMyIpAddr();
	myHostData->maxKeyCapacity;
	myHostData->next = NULL;


	//announce presence (udp), get data structures from leader (leader initiates tcp transfer)
	//if no response, I am the first
	hostList = myHostData;
	numBlocks = INIT_NUM_BLOCKS;
	blockOwner = malloc(numBlocks * sizeof(struct hostData));
	for (i = 0; i < numBlocks; i++)
	{
		blockOwner[i] = myHostData;
	}
	
	//otherwise, scan array and choose blocks to take over
	//get data from hosts that own those blocks (tcp), fill hash table
	//notify (the leader or everybody?) of ownership changes
	
	//start server (udp)
	pthread_t threadListen;
	pthread_create(&threadListen, NULL, dhtListen, NULL);
	
	return;
}

void dhtExit()
{

}

int dhtInsert(unsigned int key, unsigned int val)
{
	unsigned int dest_ip = getKeyOwner(key);
	unsigned short dest_port = LISTEN_PORT;
	struct insertCmd myMessage;
	struct insertRes response;
	int bytesReceived;

	myMessage.msgType = INSERT_COMMAND;
	myMessage.key = key;
	myMessage.val = val;
	
	bytesReceived = sendWaitForResponse(dest_ip, dest_port, (void *)&myMessage, sizeof(struct insertCmd), (void *)&response, sizeof(struct insertRes), TIMEOUT_MS, MAX_RETRIES);
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
	unsigned short dest_port = LISTEN_PORT;
	struct removeCmd myMessage;
	struct removeRes response;
	int bytesReceived;
	
	myMessage.msgType = REMOVE_COMMAND;
	myMessage.key = key;

	bytesReceived = sendWaitForResponse(dest_ip, dest_port, (void *)&myMessage, sizeof(struct removeCmd), (void *)&response, sizeof(struct removeRes), TIMEOUT_MS, MAX_RETRIES);
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
	unsigned short dest_port = LISTEN_PORT;
	struct searchCmd myMessage;
	struct searchRes response;
	int bytesReceived;

	myMessage.msgType = SEARCH_COMMAND;
	myMessage.key = key;

	bytesReceived = sendWaitForResponse(dest_ip, dest_port, (void *)&myMessage, sizeof(struct searchCmd), (void *)&response, sizeof(struct searchRes), TIMEOUT_MS, MAX_RETRIES);
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

//helper functions
void *dhtListen()
{
	struct sockaddr_in my_addr;
	struct sockaddr_in client_addr;
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

	if ((sock = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
	{
		perror("socket()");
		exit(1);
	}
	
	bzero(&my_addr, socklen);
	my_addr.sin_family=AF_INET;
	my_addr.sin_addr.s_addr=INADDR_ANY;
	my_addr.sin_port=htons(LISTEN_PORT);

	if (bind(sock, (struct sockaddr *)&my_addr, socklen) == -1)
	{
		perror("bind()");
		exit(1);
	}
//	printf("listening...\n");
	while(1)
	{
		if ((bytesReceived = recvfrom(sock, buffer, BUFFER_SIZE, 0, (struct sockaddr *)&client_addr, &socklen)) == -1)
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

//		printf("Received %d bytes from %x:%d\n", bytesReceived, client_addr.sin_addr.s_addr, client_addr.sin_port);
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
				if (getKeyOwner(insertCmdPtr->key) == myHostData->ipAddr)
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
				sendto(sock, (void *)insertResPtr, sizeof(struct insertRes), 0, (struct sockaddr *)&client_addr, socklen);
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
				if (getKeyOwner(removeCmdPtr->key) == myHostData->ipAddr)
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
				sendto(sock, (void *)removeResPtr, sizeof(struct removeRes), 0, (struct sockaddr *)&client_addr, socklen);
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
				if (getKeyOwner(searchCmdPtr->key) == myHostData->ipAddr)
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
				sendto(sock, (void *)searchResPtr, sizeof(struct searchRes), 0, (struct sockaddr *)&client_addr, socklen);
				break;
				//just ignore anything else
//			default:
//				printf("Unknown message type\n");
		}
	}
}

int sendWaitForResponse(unsigned int dest_ip, unsigned short dest_port, void *msg, unsigned int msglen, void *resBuffer, unsigned int resBufferSize, unsigned int timeout, unsigned int numRetries)
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

unsigned int getKeyOwner(unsigned int key)
{
	return blockOwner[hash(key)]->ipAddr;
}

unsigned int getMyIpAddr()
{	
	int sock;
	struct ifreq interfaceInfo;
	struct sockaddr_in *myAddr = (struct sockaddr_in *)&interfaceInfo.ifr_addr;

	memset(&interfaceInfo, 0, sizeof(struct ifreq));

	if((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0)
	{
		perror("socket");
		return 1;
	}

	strcpy(interfaceInfo.ifr_name, DEFAULT_INTERFACE);
	myAddr->sin_family = AF_INET;
	
	if(ioctl(sock, SIOCGIFADDR, &interfaceInfo) != 0)
	{
		perror("ioctl");
		return 1;
	}

	return ntohl(myAddr->sin_addr.s_addr);
}

unsigned int hash(unsigned int x)
{
	return x % numBlocks;
}

