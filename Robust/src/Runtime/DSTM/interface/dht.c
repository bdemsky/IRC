#include "dht.h"

#ifdef SIMPLE_DHT

#include <arpa/inet.h>

#define NUM_HOSTS 4
#define OIDS_PER_HOST 0x40000000

//set these to your IP addresses
unsigned int hosts[NUM_HOSTS] = {
	0xc0a802c8,
	0xc0a802c9,
	0xc0a802ca,
	0xc0a802cb,
};

//does nothing
void dhtInit(unsigned int maxKeyCapaciy)
{	return;}

//does nothing
void dhtExit()
{	return;}

//does nothing, returns 0
int dhtInsert(unsigned int key, unsigned int val)
{	return 0;}

//does nothing, returns 0
int dhtRemove(unsigned int key)
{	return 0;}

//returns 0 if successful and copies val into *val,
// 1 if key not found, -1 if an error occurred
int dhtSearch(unsigned int key, unsigned int *val)
{
	*val = hosts[key / OIDS_PER_HOST];
	return 0;
}

#else

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
#define DHT_LOG "dht.log"


#define NUM_MSG_TYPES 19

enum {
	INSERT_CMD,
	INSERT_RES,
	REMOVE_CMD,
	REMOVE_RES,
	SEARCH_CMD,
	SEARCH_RES,
	FIND_LEADER_CMD,
	FIND_LEADER_RES,
	REBUILD_REQ,
	REBUILD_RES,
	NOT_LEADER,
	REBUILD_CMD,
	JOIN_REQ,
	JOIN_RES,
	DHT_INFO_REQ,
	DHT_INFO_RES,
	FILL_DHT_CMD,
	FILL_DHT_RES,
	REBUILD_DONE_INFO
};

const char *msg_types[NUM_MSG_TYPES] =
{
	"INSERT_CMD",
	"INSERT_RES",
	"REMOVE_CMD",
	"REMOVE_RES",
	"SEARCH_CMD",
	"SEARCH_RES",
	"FIND_LEADER_CMD",
	"FIND_LEADER_RES",
	"REBUILD_REQ",
	"REBUILD_RES",
	"NOT_LEADER",
	"REBUILD_CMD",
	"JOIN_REQ",
	"JOIN_RES",
	"DHT_INFO_REQ",
	"DHT_INFO_RES",
	"FILL_DHT_CMD",
	"FILL_DHT_RES",
	"REBUILD_DONE_INFO"
};

//status codes
enum {
	INSERT_OK,
	INSERT_ERROR,
	REMOVE_OK,
	REMOVE_ERROR,
	KEY_FOUND,
	KEY_NOT_FOUND,
	NOT_KEY_OWNER,
};

struct hostData {
	unsigned int ipAddr;
	unsigned int maxKeyCapacity;
};

struct insertCmd {
	unsigned int msgType:8;
	unsigned int unused:24;
	unsigned int key;
	unsigned int val;
};

struct removeCmd {
	unsigned int msgType:8;
	unsigned int unused:24;
	unsigned int key;
};

struct searchCmd {
	unsigned int msgType:8;
	unsigned int unused:24;
	unsigned int key;
};

struct insertRes {
	unsigned int msgType:8;
	unsigned int unused:24;
	unsigned int status;
};

struct removeRes {
	unsigned int msgType:8;
	unsigned int unused:24;
	unsigned int status;
};

struct searchRes {
	unsigned int msgType:8;
	unsigned int unused:24;
	unsigned int status;
	unsigned int val;
};

struct rebuildRes {
	unsigned int msgType:8;
	unsigned int unused:24;
	unsigned int status;
};

//TODO: leave message, rebuild message...

FILE *logfile;
unsigned int leader; //ip address of leader
struct hostData myHostData;
/*----DHT data----*/
unsigned int numHosts;
struct hostData *hostArray;
unsigned int numBlocks;
unsigned int *blockOwnerArray;
/*----end DHT data----*/
pthread_t threadUdpListen;
pthread_t threadTcpListen;
int udpServerSock;
int tcpListenSock;

//return my IP address
unsigned int getMyIpAddr();
//sends broadcast to discover leader
unsigned int findLeader();
//UDP server
void *udpListen();
//TCP server
void *tcpListen();
//TCP connection handler
void *tcpAccept(void *);
//returns number of bytes received in resBuffer, or -1 if an error occurred
int udpSendWaitForResponse(unsigned int dest_ip, unsigned short dest_port,
	void *msg, unsigned int msglen, void *resBuffer, unsigned int resBufferSize,
	unsigned int timeout, unsigned int numRetries);
//returns number of bytes received in resBuffer, or -1 if an error occurred
int udpBroadcastWaitForResponse(unsigned int *reply_ip,
	unsigned short dest_port, void *msg, unsigned int msglen, void *resBuffer,
	unsigned int resBufferSize, unsigned int timeout, unsigned int numRetries);
//just UDP it
int sendNoWait(unsigned int dest_ip, unsigned short dest_port, void *msg,
	unsigned int msglen);
//right now this hashes the key into a block and returns the block owner
unsigned int getKeyOwner(unsigned int key);
//simple hash
unsigned int hash(unsigned int x);
//initiates TCP connection with leader, gets DHT data
int getDHTdata();
//outputs readable DHT data to outfile
void writeDHTdata(FILE *outfile);
void initRebuild();
void leadRebuild();
void followRebuild();

void dhtInit(unsigned int maxKeyCapacity)
{
	unsigned int myMessage;
	int bytesReceived;
	int i;
	int ret;

#ifdef DHT_LOG
	logfile = fopen(DHT_LOG, "w");
#endif

	myHostData.ipAddr = getMyIpAddr();
	myHostData.maxKeyCapacity = maxKeyCapacity;

	numHosts = numBlocks = 0;
	hostArray = NULL;
	blockOwnerArray = NULL;

	pthread_create(&threadUdpListen, NULL, udpListen, NULL);
	pthread_create(&threadTcpListen, NULL, tcpListen, NULL);

	initRebuild();

/*	leader = findLeader();

	if (leader == 0)
	{ //no response: I am the first
		leader = getMyIpAddr();

		numHosts = 1;
		hostArray = calloc(numHosts, sizeof(struct hostData));
		hostArray[0] = myHostData;
		numBlocks = INIT_BLOCK_NUM;
		blockOwnerArray = calloc(numBlocks, sizeof(unsigned int));
		for (i = 0; i < numBlocks; i++)
			blockOwnerArray[i] = 0;
	}
	else
	{
		//get DHT data from leader
		ret = getDHTdata();

		//TODO: actually, just initiate a rebuild here instead
	}
*/

	//start servers
	
	return;
}

void dhtExit()
{
	fclose(logfile);
	pthread_cancel(threadUdpListen);
	pthread_cancel(threadTcpListen);
	close(udpServerSock);
	close(tcpListenSock);
}

int dhtInsert(unsigned int key, unsigned int val)
{
	unsigned int dest_ip = getKeyOwner(key);
	struct insertCmd myMessage;
	struct insertRes response;
	int bytesReceived;

	myMessage.msgType = INSERT_CMD;
	myMessage.key = key;
	myMessage.val = val;
	
	bytesReceived = udpSendWaitForResponse(dest_ip, UDP_PORT, (void *)&myMessage,
		sizeof(struct insertCmd), (void *)&response, sizeof(struct insertRes),
		TIMEOUT_MS, MAX_RETRIES);
	if (bytesReceived == sizeof(struct insertRes))
	{
		if (response.msgType == INSERT_RES)
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
	
	myMessage.msgType = REMOVE_CMD;
	myMessage.key = key;

	bytesReceived = udpSendWaitForResponse(dest_ip, UDP_PORT, (void *)&myMessage,
		sizeof(struct removeCmd), (void *)&response, sizeof(struct removeRes),
		TIMEOUT_MS, MAX_RETRIES);
	if (bytesReceived == sizeof(struct removeRes))
	{
		if (response.msgType == REMOVE_RES)
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

	myMessage.msgType = SEARCH_CMD;
	myMessage.key = key;

	bytesReceived = udpSendWaitForResponse(dest_ip, UDP_PORT, (void *)&myMessage,
		sizeof(struct searchCmd), (void *)&response, sizeof(struct searchRes),
		TIMEOUT_MS, MAX_RETRIES);
	if (bytesReceived == sizeof(struct searchRes))
	{
		if (response.msgType == SEARCH_RES)
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

	if ((udpServerSock = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
	{
		perror("udpListen():socket()");
		pthread_exit(NULL);
	}
	
	bzero(&myAddr, socklen);
	myAddr.sin_family=AF_INET;
	myAddr.sin_addr.s_addr=INADDR_ANY;
	myAddr.sin_port=htons(UDP_PORT);

	if (bind(udpServerSock, (struct sockaddr *)&myAddr, socklen) == -1)
	{
		perror("udpListen():bind()");
		pthread_exit(NULL);
	}
#ifdef DHT_LOG
	fprintf(logfile,"udpListen(): listening on port %d\n", UDP_PORT);
	fflush(logfile);
#endif
	while(1)
	{
		if ((bytesReceived = recvfrom(udpServerSock, buffer, BUFFER_SIZE, 0,
			(struct sockaddr *)&clientAddr, &socklen)) == -1)
		{
			perror("udpListen():recvfrom()");
		}
		else if (bytesReceived == 0)
		{
#ifdef DHT_LOG
			fprintf(logfile,"udpListen(): recvfrom() returned 0\n");
			fflush(logfile);
#endif
		}
		else
		{
			gettimeofday(&now, NULL);
#ifdef DHT_LOG
			fprintf(logfile, "udpListen(): received %s from %s\n",
				(buffer[0] < NUM_MSG_TYPES ? msg_types[buffer[0]] : "unknown message"),
				inet_ntoa(clientAddr.sin_addr));
//			fprintf(logfile,"udpListen(): time received:%ds,%dus\n", now.tv_sec,
//				now.tv_usec);
//			fprintf(logfile,"udpListen(): msg size:%d bytes source:%s:%d\n",
//				bytesReceived,inet_ntoa(clientAddr.sin_addr),htons(clientAddr.sin_port));
			fflush(logfile);
#endif

			switch (buffer[0])
			{
				case INSERT_CMD:
					if (bytesReceived != sizeof(struct insertCmd))
					{
#ifdef DHT_LOG
						fprintf(logfile, "udpListen(): ERROR: incorrect message size\n");
						fflush(logfile);
#endif
						break;
					}
					insertCmdPtr = (struct insertCmd *)buffer;
#ifdef DHT_LOG
					fprintf(logfile, "udpListen(): Insert: key=%d, val=%d\n",
						insertCmdPtr->key, insertCmdPtr->val);
					fflush(logfile);
#endif
					insertResPtr = (struct insertRes *)replyBuffer;
					insertResPtr->msgType = INSERT_RES;
					if (getKeyOwner(insertCmdPtr->key) == myHostData.ipAddr)
					{
						//note: casting val to void * in order to conform to API
						if(chashInsert(myHashTable, insertCmdPtr->key,
								(void *)insertCmdPtr->val) == 0)
							insertResPtr->status = INSERT_OK;
						else
							insertResPtr->status = INSERT_ERROR;
					}
					else
					{
						insertResPtr->status = NOT_KEY_OWNER;;
					}
					if (sendto(udpServerSock, (void *)insertResPtr,
						sizeof(struct insertRes), 0, (struct sockaddr *)&clientAddr,
						socklen) == -1)
					{
						perror("udpListen():sendto()");
					}
					break;
				case REMOVE_CMD:
					if (bytesReceived != sizeof(struct removeCmd))
					{
#ifdef DHT_LOG
						fprintf(logfile, "udpListen(): ERROR: incorrect message size\n");
						fflush(logfile);
#endif
						break;
					}
					removeCmdPtr = (struct removeCmd *)buffer;
#ifdef DHT_LOG
					fprintf(logfile,"udpListen(): Remove: key=%d\n", removeCmdPtr->key);
					fflush(logfile);
#endif
					removeResPtr = (struct removeRes *)replyBuffer;
					removeResPtr->msgType = REMOVE_RES;
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
					if (sendto(udpServerSock, (void *)removeResPtr, sizeof(struct removeRes), 0,
						(struct sockaddr *)&clientAddr, socklen) == -1)
					{
						perror("udpListen():sendto()");
					}
					break;
				case SEARCH_CMD:
					if (bytesReceived != sizeof(struct searchCmd))
					{
#ifdef DHT_LOG
						fprintf(logfile,"udpListen(): ERROR: incorrect message size\n");
						fflush(logfile);
#endif
						break;
					}
					searchCmdPtr = (struct searchCmd *)buffer;
#ifdef DHT_LOG
						fprintf(logfile,"udpListen(): Search: key=%d\n",searchCmdPtr->key);
						fflush(logfile);
#endif
					searchResPtr = (struct searchRes *)replyBuffer;
					searchResPtr->msgType = SEARCH_RES;
					if (getKeyOwner(searchCmdPtr->key) == myHostData.ipAddr)
					{
						//note: casting val to void * in order to conform to API
						if((searchResPtr->val = (unsigned int)chashSearch(myHashTable,
								searchCmdPtr->key)) == 0)
							searchResPtr->status = KEY_NOT_FOUND;
						else
							searchResPtr->status = KEY_FOUND;
					}
					else
					{
						searchResPtr->status = NOT_KEY_OWNER;
					}
					if (sendto(udpServerSock, (void *)searchResPtr, sizeof(struct searchRes), 0,
						(struct sockaddr *)&clientAddr, socklen) == -1)
					{
						perror("udpListen():sendto()");
					}
					break;
				case FIND_LEADER_CMD:
					if (bytesReceived != sizeof(char))
					{
#ifdef DHT_LOG
						fprintf(logfile, "udpListen(): ERROR: incorrect message size\n");
						fflush(logfile);
#endif
						break;
					}
					if (leader == getMyIpAddr())
					{
						replyBuffer[0] = FIND_LEADER_RES;
						if(sendto(udpServerSock, (void *)replyBuffer, sizeof(char), 0,
							(struct sockaddr *)&clientAddr, socklen) == -1)
						{
							perror("udpListen():sendto");
						}
					}
					break;
				case REBUILD_REQ:
					if (bytesReceived != sizeof(char))
					{
#ifdef DHT_LOG
						fprintf(logfile, "udpListen(): ERROR: incorrect message size\n");
						fflush(logfile);
#endif
						break;
					}
					if (leader == getMyIpAddr())
					{
						replyBuffer[0] = REBUILD_RES;
						if(sendto(udpServerSock, (void *)replyBuffer, sizeof(char), 0,
							(struct sockaddr *)&clientAddr, socklen) == -1)
						{
							perror("udpListen():sendto");
						}
						//TODO: leadRebuild()
					}
					else
					{
						replyBuffer[0] = NOT_LEADER;
						if(sendto(udpServerSock, (void *)replyBuffer, sizeof(char), 0,
							(struct sockaddr *)&clientAddr, socklen) == -1)
						{
							perror("udpListen():sendto");
						}
					}
					break;
//				default:
#ifdef DHT_LOG
//					fprintf(logfile,"udpListen(): ERROR: Unknown message type\n");
//					fflush(logfile);
#endif
			}
		}
	}
}

int udpSendWaitForResponse(unsigned int dest_ip, unsigned short dest_port,
	void *msg, unsigned int msglen, void *resBuffer, unsigned int resBufferSize,
	unsigned int timeout, unsigned int numRetries)
{
	struct sockaddr_in server_addr;
	struct sockaddr_in ack_addr;
	socklen_t socklen = sizeof(struct sockaddr_in);
	struct pollfd pollsock;
	struct timeval now;
	int retval;
	int i;
	ssize_t bytesReceived;

	bzero((char *) &server_addr, sizeof(server_addr));
	server_addr.sin_family = AF_INET;
	server_addr.sin_port = htons(dest_port);
	server_addr.sin_addr.s_addr = htonl(dest_ip);

	if ((pollsock.fd = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
	{
		perror("udpSendWaitForResponse():socket()");
		return -1;
	}
	
	pollsock.events = POLLIN;
	
	for (i = 0; i < MAX_RETRIES; i++)
	{
#ifdef DHT_LOG
		if (i > 0)
			fprintf(logfile,"udpSendWaitForResponse(): trying again, count: %d\n",
				i+1);
		fflush(logfile);
#endif
		if (sendto(pollsock.fd, msg, msglen, 0, (struct sockaddr *)&server_addr,
			socklen) == -1)
		{
			perror("udpSendWaitForResponse():sendto");
			return -1;
		}
#ifdef DHT_LOG
		gettimeofday(&now, NULL);
		fprintf(logfile,"udpSendWaitForResponse(): message sent:%ds,%dus\n",
			now.tv_sec, now.tv_usec);
		fflush(logfile);
#endif
		retval = poll(&pollsock, 1, timeout);
		if (retval !=0)
		{
			bytesReceived = recvfrom(pollsock.fd, resBuffer, resBufferSize, 0,
				(struct sockaddr *)&ack_addr, &socklen);
			if ((ack_addr.sin_addr.s_addr == server_addr.sin_addr.s_addr)
			&& (ack_addr.sin_port == server_addr.sin_port))
			{
				close(pollsock.fd);
#ifdef DHT_LOG
				gettimeofday(&now, NULL);
				fprintf(logfile,"udpSendWaitForResponse(): received response:%ds,%dus\n", now.tv_sec, now.tv_usec);
				fflush(logfile);
#endif
				return bytesReceived;
			}
		}
	}
	close(pollsock.fd);
#ifdef DHT_LOG
	gettimeofday(&now, NULL);
	printf("udpSendWaitForResponse(): timed out, no ack:%ds,%dus\n",
		now.tv_sec, now.tv_usec);
	fflush(logfile);
#endif
	return -1;
}

int udpBroadcastWaitForResponse(unsigned int *reply_ip,
	unsigned short dest_port, void *msg, unsigned int msglen, void *resBuffer,
	unsigned int resBufferSize, unsigned int timeout, unsigned int numRetries)
{
	struct sockaddr_in server_addr;
	struct sockaddr_in ack_addr;
	socklen_t socklen = sizeof(struct sockaddr_in);
	struct pollfd pollsock;
	struct timeval now;
	int retval;
	int i;
	ssize_t bytesReceived;
	int on;

	bzero((char *) &server_addr, sizeof(server_addr));
	server_addr.sin_family = AF_INET;
	server_addr.sin_port = htons(dest_port);
	server_addr.sin_addr.s_addr = htonl(0xFFFFFFFF);

	if ((pollsock.fd = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
	{
		perror("udpBroadcastWaitForResponse():socket()");
		return -1;
	}

	on = 1;
	if (setsockopt(pollsock.fd, SOL_SOCKET, SO_BROADCAST, &on, sizeof(on)) == -1)
	{
		perror("udpBroadcastWaitForResponse():setsockopt()");
		return -1;
	}

	pollsock.events = POLLIN;
	
	for (i = 0; i < MAX_RETRIES; i++)
	{
#ifdef DHT_LOG
		if (i > 0)
			fprintf(logfile,"udpBroadcastWaitForResponse(): trying again, count: %d\n", i+1);
			fflush(logfile);
#endif
		if (sendto(pollsock.fd, msg, msglen, 0, (struct sockaddr *)&server_addr,
			socklen) == -1)
		{
			perror("udpBroadcastWaitForResponse():sendto()");
			return -1;
		}
#ifdef DHT_LOG
		gettimeofday(&now, NULL);
		fprintf(logfile,"udpBroadcastWaitForResponse(): message sent:%ds,%dus\n",
			now.tv_sec, now.tv_usec);
		fflush(logfile);
#endif
		retval = poll(&pollsock, 1, timeout);
		if (retval !=0)
		{
			bytesReceived = recvfrom(pollsock.fd, resBuffer, resBufferSize, 0,
				(struct sockaddr *)&ack_addr, &socklen);
			close(pollsock.fd);
			*reply_ip = htonl(ack_addr.sin_addr.s_addr);
#ifdef DHT_LOG
			gettimeofday(&now, NULL);
			fprintf(logfile,"udpBroadcastWaitForResponse(): received response:%ds,%dus\n", now.tv_sec, now.tv_usec);
			fflush(logfile);
#endif
			return bytesReceived;
		}
	}
	close(pollsock.fd);
#ifdef DHT_LOG
	gettimeofday(&now, NULL);
	fprintf(logfile,"udpBroadcastWaitForResponse(): timed out, no ack:%ds,%dus\n",
		now.tv_sec, now.tv_usec);
	fflush(logfile);
#endif
	return -1;
}

// use TCP for potentially large and/or important data transfer
void *tcpListen()
{
	struct sockaddr_in myAddr;
	struct sockaddr_in clientAddr;
	int tcpAcceptSock;
	socklen_t socklen = sizeof(struct sockaddr_in);
	pthread_t threadTcpAccept;

	tcpListenSock = socket(AF_INET, SOCK_STREAM, 0);
	if (tcpListenSock == -1)
	{
		perror("tcpListen():socket()");
		pthread_exit(NULL);
	}

	myAddr.sin_family = AF_INET;
	myAddr.sin_port = htons(TCP_PORT);
	myAddr.sin_addr.s_addr = INADDR_ANY;
	memset(&(myAddr.sin_zero), '\0', 8);

	if (bind(tcpListenSock, (struct sockaddr *)&myAddr, socklen) == -1)
	{
		perror("tcpListen():socket()");
		pthread_exit(NULL);
	}

	if (listen(tcpListenSock, BACKLOG) == -1)
	{
		perror("tcpListen():listen()");
		pthread_exit(NULL);
	}

#ifdef DHT_LOG
	fprintf(logfile,"tcpListen(): listening on port %d\n", TCP_PORT);
	fflush(logfile);
#endif

	while(1)
	{
		tcpAcceptSock = accept(tcpListenSock, (struct sockaddr *)&clientAddr, &socklen);
		pthread_create(&threadTcpAccept, NULL, tcpAccept, (void *)tcpAcceptSock);
	}
}

void *tcpAccept(void *arg)
{
	int tcpAcceptSock = (int)arg;
	int bytesReceived;
	char msgType;

#ifdef DHT_LOG
	fprintf(logfile, "tcpAccept(): accepted tcp connection, file descriptor: %d\n", tcpAcceptSock);
	fflush(logfile);
#endif

	bytesReceived = recv(tcpAcceptSock, &msgType, sizeof(char), 0);
	if (bytesReceived == -1)
	{
		perror("tcpAccept():recv()");
	}
	else if (bytesReceived == 0)
	{
#ifdef DHT_LOG
		fprintf(logfile, "tcpAccept(): bytesReceived = 0\n", tcpAcceptSock);
		fflush(logfile);
#endif
	}
	else
	{
		switch (msgType)
		{
			case DHT_INFO_REQ:
				if (send(tcpAcceptSock, &numHosts, sizeof(numHosts), 0) == -1)
				{
					perror("tcpAccept():send()");
					break;
				}
				if (send(tcpAcceptSock, &numBlocks, sizeof(numBlocks), 0) == -1)
				{
					perror("tcpAccept():send()");
					break;
				}
				if (send(tcpAcceptSock, hostArray, numHosts*sizeof(struct hostData),
						0) == -1)
				{
					perror("tcpAccept():send()");
					break;
				}
				if (send(tcpAcceptSock, blockOwnerArray, numBlocks*sizeof(unsigned int),
						0) == -1)
				{
					perror("tcpAccept():send()");
					break;
				}
				break;
			default:
#ifdef DHT_LOG
				fprintf(logfile, "tcpAccept(): unrecognized msg type\n");
				fflush(logfile);
#endif
		}
	}

	if (close(tcpAcceptSock) == -1)
	{
		perror("tcpAccept():close()");
	}

#ifdef DHT_LOG
	fprintf(logfile, "tcpAccept(): closed tcp connection, file descriptor: %d\n",
		tcpAcceptSock);
	fflush(logfile);
#endif

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
		perror("getMyIpAddr():socket()");
		return 1;
	}

	strcpy(interfaceInfo.ifr_name, DEFAULT_INTERFACE);
	myAddr->sin_family = AF_INET;
	
	if(ioctl(sock, SIOCGIFADDR, &interfaceInfo) != 0)
	{
		perror("getMyIpAddr():ioctl()");
		return 1;
	}

	return ntohl(myAddr->sin_addr.s_addr);
}

unsigned int findLeader()
{
	unsigned int reply_ip;
	int bytesReceived;
	char myMessage;
	char response;

#ifdef DHT_LOG
	fprintf(logfile, "findLeader(): broadcasting...\n");
	fflush(logfile);
#endif

	myMessage = FIND_LEADER_CMD;

	bytesReceived = udpBroadcastWaitForResponse(&reply_ip, UDP_PORT,
		(void *)&myMessage, sizeof(myMessage), (void *)&response,
		sizeof(response), TIMEOUT_MS, MAX_RETRIES);

	if (bytesReceived == -1)
	{
#ifdef DHT_LOG
		fprintf(logfile, "findLeader(): no response\n");
		fflush(logfile);
#endif
		return 0;
	}
	else if (response == FIND_LEADER_RES)
	{
#ifdef DHT_LOG
		struct in_addr reply_addr;
		reply_addr.s_addr = htonl(reply_ip);
		fprintf(logfile, "findLeader(): leader found:%s\n",
					inet_ntoa(reply_addr));
		fflush(logfile);
#endif
		return reply_ip;
	}
	else
	{
#ifdef DHT_LOG
		fprintf(logfile, "findLeader(): unexpected response\n");
		fflush(logfile);
#endif
		return 0;
	}
}

int getDHTdata()
{
	struct sockaddr_in leader_addr;
	int sock;
	char msg;
	int bytesReceived;

	if ((sock = socket(AF_INET, SOCK_STREAM, 0)) == -1)
	{
		perror("getDHTdata():socket()");
		return -1;
	}

	bzero((char *)&leader_addr, sizeof(leader_addr));
	leader_addr.sin_family = AF_INET;
	leader_addr.sin_port = htons(TCP_PORT);
	leader_addr.sin_addr.s_addr = htonl(leader);

	if (connect(sock, (struct sockaddr *)&leader_addr, sizeof(leader_addr)) == -1)
	{
		perror("getDHTdata():connect()");
		close(sock);
		return -1;
	}
	msg = DHT_INFO_REQ;
	if (send(sock, &msg, sizeof(char), 0) == -1)
	{
		perror("getDHTdata():send()");
		close(sock);
		return -1;
	}
	bytesReceived = recv(sock, &numHosts, sizeof(numHosts), 0);
	if (bytesReceived == -1)
	{
		perror("getDHTdata():recv()");
		close(sock);
		return -1;
	}
	if (bytesReceived != sizeof(numHosts))
	{
#ifdef DHT_LOG
		fprintf(logfile,"getDHTdata(): ERROR: numHosts not completely received\n");
		fflush(logfile);
#endif
		close(sock);
		return -1;
	}
	bytesReceived = recv(sock, &numBlocks, sizeof(numBlocks), 0);
	if (bytesReceived == -1)
	{
		perror("getDHTdata():recv()");
		close(sock);
		return -1;
	}
	if (bytesReceived != sizeof(numBlocks))
	{
#ifdef DHT_LOG
		fprintf(logfile,"getDHTdata(): ERROR: numBlocks not completely received\n");
		fflush(logfile);
#endif
		close(sock);
		return -1;
	}
	if (hostArray != NULL)
		free(hostArray);
	hostArray = calloc(numHosts, sizeof(struct hostData));
	bytesReceived = recv(sock, hostArray, numHosts*sizeof(struct hostData), 0);
	if (bytesReceived == -1)
	{
		perror("getDHTdata():recv()");
		close(sock);
		return -1;
	}
	if (bytesReceived != numHosts*sizeof(struct hostData))
	{
#ifdef DHT_LOG
		fprintf(logfile,"getDHTdata(): ERROR: hostArray not completely received\n");
		fflush(logfile);
#endif
		close(sock);
		return -1;
	}
	if (blockOwnerArray != NULL)
		free(blockOwnerArray);
	blockOwnerArray = calloc(numBlocks, sizeof(unsigned int));
	bytesReceived = recv(sock, blockOwnerArray, numBlocks*sizeof(unsigned int), 0);
	if (bytesReceived == -1)
	{
		perror("getDHTdata():recv()");
		close(sock);
		return -1;
	}
	if (bytesReceived != numBlocks*sizeof(unsigned int))
	{
#ifdef DHT_LOG
		fprintf(logfile,"getDHTdata(): ERROR: blockOwnerArray not completely received\n");
		fflush(logfile);
#endif
		close(sock);
		return -1;
	}
#ifdef DHT_LOG
		fprintf(logfile,"getDHTdata(): got data:\n");
		writeDHTdata(logfile);
		fflush(logfile);
#endif
	return 0;
}

unsigned int hash(unsigned int x)
{
	return x % numBlocks;
}

//This function will not return until it succeeds in submitting
// a rebuild request to the leader. It is then the leader's responibility
// to ensure that the rebuild is caried out
void initRebuild()
{
	int bytesReceived;
	char msg;
	char response;
	int done;
	int retry_count;
	int i;

	done = 0;
	retry_count = 0;

	while (!done)
	{
#ifdef DHT_LOG
		if (retry_count > 0)
		{
			fprintf(logfile,"initRebuild(): retry count:%d\n", retry_count);
			fflush(logfile);
		}
#endif

		if (leader == 0 || retry_count > 0)
		{
			leader = findLeader(); //broadcast
			if (leader == 0) //no response
			{
				//TODO:elect leader: this will do for now
				leader = getMyIpAddr();

				numHosts = 1;
				hostArray = calloc(numHosts, sizeof(struct hostData));
				hostArray[0] = myHostData;
				numBlocks = INIT_BLOCK_NUM;
				blockOwnerArray = calloc(numBlocks, sizeof(unsigned int));
				for (i = 0; i < numBlocks; i++)
					blockOwnerArray[i] = 0;
			}
		}
	
		msg = REBUILD_REQ;

		bytesReceived = udpSendWaitForResponse(leader, UDP_PORT,
			(void *)&msg, sizeof(msg), (void *)&response, sizeof(response),
			TIMEOUT_MS, MAX_RETRIES);
		if (bytesReceived == -1)
		{
			perror("initRebuild():recv()");
		}
		else if (bytesReceived != sizeof(response))
		{
#ifdef DHT_LOG
			fprintf(logfile,"initRebuild(): ERROR: response not completely received\n");
			fflush(logfile);
#endif
		}
		else if (response == NOT_LEADER)
		{
#ifdef DHT_LOG
			struct in_addr address;
			address.s_addr = htonl(leader);
			fprintf(logfile,"initRebuild(): ERROR: %s no longer leader\n",
				inet_ntoa(address));
			fflush(logfile);
#endif
		}
		else if (response != REBUILD_RES)
		{
#ifdef DHT_LOG
			fprintf(logfile,"initRebuild(): ERROR: unexpected response\n");
			fflush(logfile);
#endif
		}
		else
		{
#ifdef DHT_LOG
			fprintf(logfile,"initRebuild(): submitted rebuild request\n");
			writeDHTdata(logfile);
			fflush(logfile);
#endif
			done = 1;
		}
	}
	return;
}

void leadRebuild()
{
	
}

void followRebuild()
{

}

void writeDHTdata(FILE *outfile)
{
	int i;
	struct in_addr address;
	fprintf(outfile,"numHosts=%d,numBlocks=%d\n", numHosts, numBlocks);
	fprintf(outfile,"hostArray: index: ipAddr, maxKeyCapacity\n");
	for (i = 0; i < numHosts; i++)
	{
		address.s_addr = htonl(hostArray[i].ipAddr);
		fprintf(outfile,"%d: %s, %d\n", i, inet_ntoa(address),
			hostArray[i].maxKeyCapacity);
	}
	fprintf(outfile,"blockOwnerArray: index: blockOwner\n");
	for (i = 0; i < numBlocks; i++)
	{
		fprintf(outfile,"%d: %d\n", i, blockOwnerArray[i]);
	}
}

#endif

