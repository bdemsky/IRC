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

/*******************************************************************************
*                              Includes
*******************************************************************************/

#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/ioctl.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <pthread.h>
#include <sys/time.h>
#include <sys/poll.h>
#include <netdb.h>
#include <net/if.h>
#include <linux/sockios.h>
#include <sys/time.h>
#include "clookup.h" //this works for now, do we need anything better?

/*******************************************************************************
*                           Local Defines, Structs
*******************************************************************************/

#define BUFFER_SIZE 512 //maximum message size
#define UDP_PORT 2157
#define TCP_PORT 2157
#define BACKLOG 10 //max pending tcp connections
#define TIMEOUT_MS 500
#define MAX_RETRIES 3
#define INIT_HOST_ALLOC 1
#define INIT_BLOCK_NUM 1
#define DEFAULT_INTERFACE "eth0"
#define DHT_LOG "dht.log"

//make sure this is consistent with enum below
#define NUM_MSG_TYPES 20

//make sure this matches msg_types global var
enum {
	INSERT_CMD,
	INSERT_RES,
	REMOVE_CMD,
	REMOVE_RES,
	SEARCH_CMD,
	SEARCH_RES,
	FIND_LEADER_REQ,
	FIND_LEADER_RES,
	REBUILD_REQ,
	REBUILD_RES,
	NOT_LEADER,
	REBUILD_CMD,
	JOIN_REQ,
	JOIN_RES,
	GET_DHT_INFO_CMD,
	DHT_INFO_REQ,
	DHT_INFO_RES,
	FILL_DHT_CMD,
	FILL_DHT_RES,
	REBUILD_DONE_INFO
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

enum {
	NORMAL_STATE,
	REBUILD1_STATE,
	REBUILD2_STATE,
	REBUILD3_STATE,
	LEAD_NORMAL_STATE,
	LEAD_REBUILD1_STATE,
	LEAD_REBUILD2_STATE,
	LEAD_REBUILD3_STATE
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

struct joinReq {
	unsigned int msgType:8;
	unsigned int unused:24;
	struct hostData newHostData;
};

/*******************************************************************************
*                           Global Variables
*******************************************************************************/

//make sure this matches enumeration above
const char *msg_types[NUM_MSG_TYPES] =
{
	"INSERT_CMD",
	"INSERT_RES",
	"REMOVE_CMD",
	"REMOVE_RES",
	"SEARCH_CMD",
	"SEARCH_RES",
	"FIND_LEADER_REQ",
	"FIND_LEADER_RES",
	"REBUILD_REQ",
	"REBUILD_RES",
	"NOT_LEADER",
	"REBUILD_CMD",
	"JOIN_REQ",
	"JOIN_RES",
	"GET_DHT_INFO_CMD",
	"DHT_INFO_REQ",
	"DHT_INFO_RES",
	"FILL_DHT_CMD",
	"FILL_DHT_RES",
	"REBUILD_DONE_INFO"
};

FILE *logfile;
//ip address of leader
unsigned int leader;
//set by dhtInit()
struct hostData myHostData;
//number of hosts in the system
unsigned int numHosts;
//ip address and max key capacity of each host
struct hostData *hostArray;
//memory allocated for this many items in hostArray
unsigned int hostArraySize;
//number of keyspace divisions, preferably a power of 2 > numHosts
unsigned int numBlocks;
//this array has numBlocks elements, each of which contains an index to hostArray
// the key owner is found by hashing the key into one of these blocks and using this
// array to find the corresponding host in hostArray
unsigned int *blockOwnerArray;
//used by leader to track which hosts have responded, etc.
unsigned int *hostRebuildStates;
//thread handles
pthread_t threadUdpListen;
pthread_t threadTcpListen;
//server sockets
struct pollfd udpServerPollSock;
int tcpListenSock;
//see above for enumeration of states
int state;

/*******************************************************************************
*                         Local Function Prototypes
*******************************************************************************/

//log funtion, use like printf()
void dhtLog(const char *format, ...);
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
//sends REBUILD_REQ to leader, retries until leader responds, or causes new leader to be chosen
void initRebuild();
//adds entry to end of hostArray, increments numHosts,
// allocates more space if necessary
void addHost(struct hostData newHost);
//initiates TCP connection with leader, gets DHT data
int getDHTdata();
//outputs readable DHT data to outfile
void writeDHTdata(FILE *outfile);
void clearDHTdata();
void initDHTdata();
void makeAssignments();
//returns not-zero if ok, zero if not ok
int msgSizeOk(unsigned char type, unsigned int size);

/*******************************************************************************
*                      Global Function Definitions
*******************************************************************************/

void dhtInit(unsigned int maxKeyCapacity)
{
	unsigned int myMessage;
	int bytesReceived;
	int i;
	int ret;

	logfile = fopen(DHT_LOG, "w");
	dhtLog("dhtInit() - initializing...\n");

	myHostData.ipAddr = getMyIpAddr();
	myHostData.maxKeyCapacity = maxKeyCapacity;

	numHosts = numBlocks = hostArraySize = 0;
	hostArray = NULL;
	blockOwnerArray = NULL;
	hostRebuildStates = NULL;

	state = NORMAL_STATE;

	pthread_create(&threadUdpListen, NULL, udpListen, NULL);
	pthread_create(&threadTcpListen, NULL, tcpListen, NULL);

	initRebuild();

	return;
}

void dhtExit()
{
	dhtLog("dhtExit(): cleaning up...\n");
	fclose(logfile);
	pthread_cancel(threadUdpListen);
	pthread_cancel(threadTcpListen);
	close(udpServerPollSock.fd);
	close(tcpListenSock);
	clearDHTdata();
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

/*******************************************************************************
*                      Local Function Definitions
*******************************************************************************/

//use UDP for messages that are frequent and short
void *udpListen()
{
	struct sockaddr_in myAddr;
	struct sockaddr_in clientAddr;
	struct sockaddr_in bcastAddr;
	socklen_t socklen = sizeof(struct sockaddr_in);
	char buffer[BUFFER_SIZE];
	ssize_t bytesReceived;
	struct insertCmd *insertCmdPtr;
	struct removeCmd *removeCmdPtr;
	struct searchCmd *searchCmdPtr;
	struct insertRes *insertResPtr;
	struct removeRes *removeResPtr;
	struct searchRes *searchResPtr;
	struct joinReq *joinReqPtr;
	char replyBuffer[BUFFER_SIZE];
	struct timeval now;
	struct timeval rebuild1Timeout;
	int rebuild1TimerSet;
	int on;
	int pollret;
	int i;

	chashtable_t *myHashTable = chashCreate(HASH_SIZE, LOADFACTOR);

	if ((udpServerPollSock.fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
	{
		perror("udpListen():socket()");
		pthread_exit(NULL);
	}

	on = 1;
	if (setsockopt(udpServerPollSock.fd, SOL_SOCKET, SO_BROADCAST, &on,
		sizeof(on)) == -1)
	{
		perror("udpBroadcastWaitForResponse():setsockopt()");
		pthread_exit(NULL);
	}
	
	udpServerPollSock.events = POLLIN;
	
	bzero(&myAddr, socklen);
	myAddr.sin_family = AF_INET;
	myAddr.sin_addr.s_addr = htonl(INADDR_ANY);
	myAddr.sin_port = htons(UDP_PORT);

	bzero(&bcastAddr, socklen);
	bcastAddr.sin_family = AF_INET;
	bcastAddr.sin_addr.s_addr = htonl(0xFFFFFFFF);
	bcastAddr.sin_port = htons(UDP_PORT);

	if (bind(udpServerPollSock.fd, (struct sockaddr *)&myAddr, socklen) == -1)
	{
		perror("udpListen():bind()");
		pthread_exit(NULL);
	}
	dhtLog("udpListen(): listening on port %d\n", UDP_PORT);

	rebuild1TimerSet = 0;
	while(1)
	{
		pollret = poll(&udpServerPollSock, 1, TIMEOUT_MS);
		if (pollret < 0)
		{	perror("udpListen():poll()");	}
		else if (pollret > 0)
		{
			if ((bytesReceived = recvfrom(udpServerPollSock.fd, buffer, BUFFER_SIZE,
				0, (struct sockaddr *)&clientAddr, &socklen)) == -1)
			{	perror("udpListen():recvfrom()");	}
			else if (bytesReceived == 0)
			{
				dhtLog("udpListen(): recvfrom() returned 0\n");
			}
			else
			{
				dhtLog("udpListen(): received %s from %s\n",
					(buffer[0] < NUM_MSG_TYPES ? msg_types[buffer[0]] :
					"unknown message"), inet_ntoa(clientAddr.sin_addr));
				if (!msgSizeOk(buffer[0], bytesReceived))
				{
					dhtLog("udpListen(): ERROR: incorrect message size\n");
				}
				else
				{
					switch (buffer[0])
					{
						case INSERT_CMD:
							if (state == NORMAL_STATE || state == LEAD_NORMAL_STATE
								|| state == REBUILD3_STATE || state == LEAD_REBUILD3_STATE)
							{
								insertCmdPtr = (struct insertCmd *)buffer;
								dhtLog( "udpListen(): Insert: key=%d, val=%d\n",
									insertCmdPtr->key, insertCmdPtr->val);
								insertResPtr = (struct insertRes *)replyBuffer;
								insertResPtr->msgType = INSERT_RES;
								insertResPtr->unused = 0;
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
								if (sendto(udpServerPollSock.fd, (void *)insertResPtr,
									sizeof(struct insertRes), 0, (struct sockaddr *)&clientAddr,
									socklen) == -1)
								{	perror("udpListen():sendto()");	}
							}
							break;
						case REMOVE_CMD:
							if (state == NORMAL_STATE || state == LEAD_NORMAL_STATE)
							{
								removeCmdPtr = (struct removeCmd *)buffer;
								dhtLog("udpListen(): Remove: key=%d\n", removeCmdPtr->key);
								removeResPtr = (struct removeRes *)replyBuffer;
								removeResPtr->msgType = REMOVE_RES;
								removeResPtr->unused = 0;
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
								if (sendto(udpServerPollSock.fd, (void *)removeResPtr,
									sizeof(struct removeRes), 0, (struct sockaddr *)&clientAddr,
									socklen) == -1)
								{	perror("udpListen():sendto()");	}
							}
							break;
						case SEARCH_CMD:
							if (state == NORMAL_STATE || state == LEAD_NORMAL_STATE)
							{
								searchCmdPtr = (struct searchCmd *)buffer;
								dhtLog("udpListen(): Search: key=%d\n",searchCmdPtr->key);
								searchResPtr = (struct searchRes *)replyBuffer;
								searchResPtr->msgType = SEARCH_RES;
								searchResPtr->unused = 0;
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
								if (sendto(udpServerPollSock.fd, (void *)searchResPtr,
									sizeof(struct searchRes), 0, (struct sockaddr *)&clientAddr,
									socklen) == -1)
								{	perror("udpListen():sendto()");	}
							}
							break;
						case FIND_LEADER_REQ:
							if (state == LEAD_NORMAL_STATE || state == LEAD_REBUILD1_STATE
								|| state == LEAD_REBUILD2_STATE || state == LEAD_REBUILD3_STATE)
							{
								replyBuffer[0] = FIND_LEADER_RES;
								if(sendto(udpServerPollSock.fd, (void *)replyBuffer,
									sizeof(char), 0,(struct sockaddr *)&clientAddr, socklen) == -1)
								{	perror("udpListen():sendto()");	}
							}
							break;
						case REBUILD_REQ:
							if (state == LEAD_NORMAL_STATE || state == LEAD_REBUILD1_STATE
								|| state == LEAD_REBUILD2_STATE || state == LEAD_REBUILD3_STATE)
							{
								replyBuffer[0] = REBUILD_RES;
								if (sendto(udpServerPollSock.fd, (void *)replyBuffer,
									sizeof(char), 0, (struct sockaddr *)&clientAddr, socklen) == -1)
								{	perror("udpListen():sendto()");	}
								if (gettimeofday(&rebuild1Timeout, NULL) < 0)
								{	perror("dhtLog():gettimeofday()"); }
								//TODO: make this a configurable parameter
								rebuild1Timeout.tv_sec += 3;
								rebuild1TimerSet = 1;
								//clear out previous host data
								numHosts = 1;
								hostArray[0] = myHostData;

								state = LEAD_REBUILD1_STATE;

								replyBuffer[0] = REBUILD_CMD;
								if (sendto(udpServerPollSock.fd, (void *)replyBuffer,
									sizeof(char), 0, (struct sockaddr *)&bcastAddr, socklen) == -1)
								{	perror("udpListen():sendto()");	}
								
							}
							else
							{
								replyBuffer[0] = NOT_LEADER;
								if(sendto(udpServerPollSock.fd, (void *)replyBuffer,
									sizeof(char), 0,(struct sockaddr *)&clientAddr, socklen) == -1)
								{	perror("udpListen():sendto()");	}
							}
						case REBUILD_CMD:
							if (state != LEAD_REBUILD1_STATE)
							{
								//consider this an official declaration of authority,
								// in case I was confused about this
								leader = htonl(clientAddr.sin_addr.s_addr);
								
								clearDHTdata();

								joinReqPtr = (struct joinReq *)replyBuffer;
								joinReqPtr->msgType = JOIN_REQ;
								joinReqPtr->unused = 0;
								joinReqPtr->newHostData = myHostData;
								//note: I'm reusing bytesReceived and buffer
								bytesReceived = udpSendWaitForResponse(leader, UDP_PORT,
									(void *)replyBuffer, sizeof(struct joinReq), (void *)buffer,
									BUFFER_SIZE, TIMEOUT_MS, MAX_RETRIES);
								if ((bytesReceived == sizeof(char)) && (buffer[0] == JOIN_RES))
									state = REBUILD1_STATE;
								else
									initRebuild();
							}
							break;
						case JOIN_REQ:
							if (state == LEAD_REBUILD1_STATE)
							{
								joinReqPtr = (struct joinReq *)buffer;
								addHost(joinReqPtr->newHostData);

								replyBuffer[0] = JOIN_RES;
								if (sendto(udpServerPollSock.fd, (void *)replyBuffer,
									sizeof(char), 0,(struct sockaddr *)&clientAddr, socklen) == -1)
								{	perror("udpListen():sendto()");	}
							}
							break;
						case GET_DHT_INFO_CMD:
							if (state == REBUILD1_STATE)
							{
								getDHTdata();
								state = REBUILD2_STATE;
							}
							break;
						default:
							dhtLog("udpListen(): ERROR: Unknown message type\n");
					}
				}
			}
		} //end (pollret > 0)
		else // (pollret == 0), timeout
		{
			if (gettimeofday(&now, NULL) < 0)
			{	perror("dhtLog():gettimeofday()"); }
			if (rebuild1TimerSet && timercmp(&now, &rebuild1Timeout, >))
			{
				rebuild1TimerSet = 0;
				if (state == LEAD_REBUILD1_STATE)
				{
					makeAssignments();
					dhtLog("udpListen(): assignments made\n");
					writeDHTdata(logfile);
					if (hostRebuildStates != NULL)
						free(hostRebuildStates);
					hostRebuildStates = calloc(numHosts, sizeof(unsigned int));
					for (i = 0; i < numHosts; i++)
						hostRebuildStates[i] = REBUILD1_STATE;
					state = LEAD_REBUILD2_STATE;
					replyBuffer[0] = GET_DHT_INFO_CMD;
					if (sendto(udpServerPollSock.fd, (void *)replyBuffer,
						sizeof(char), 0, (struct sockaddr *)&bcastAddr, socklen) == -1)
					{	perror("udpListen():sendto()");	}
				}
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
		if (i > 0)
			dhtLog("udpSendWaitForResponse(): trying again, count: %d\n", i+1);
		if (sendto(pollsock.fd, msg, msglen, 0, (struct sockaddr *)&server_addr,
			socklen) == -1)
		{
			perror("udpSendWaitForResponse():sendto");
			return -1;
		}
		dhtLog("udpSendWaitForResponse(): message sent\n");
		retval = poll(&pollsock, 1, timeout);
		if (retval < 0)
		{
			perror("udpSendWaitForResponse():poll()");
		}
		else if (retval > 0)
		{
			bytesReceived = recvfrom(pollsock.fd, resBuffer, resBufferSize, 0,
				(struct sockaddr *)&ack_addr, &socklen);
			if ((ack_addr.sin_addr.s_addr == server_addr.sin_addr.s_addr)
			&& (ack_addr.sin_port == server_addr.sin_port))
			{
				close(pollsock.fd);
				dhtLog("udpSendWaitForResponse(): received response\n");
				return bytesReceived;
			}
		}
	}
	close(pollsock.fd);
	printf("udpSendWaitForResponse(): timed out, no ack\n");
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
		if (i > 0)
			dhtLog("udpBroadcastWaitForResponse(): trying again, count: %d\n", i+1);
		if (sendto(pollsock.fd, msg, msglen, 0, (struct sockaddr *)&server_addr,
			socklen) == -1)
		{
			perror("udpBroadcastWaitForResponse():sendto()");
			return -1;
		}
		dhtLog("udpBroadcastWaitForResponse(): message sent\n");
		retval = poll(&pollsock, 1, timeout);
		if (retval !=0)
		{
			bytesReceived = recvfrom(pollsock.fd, resBuffer, resBufferSize, 0,
				(struct sockaddr *)&ack_addr, &socklen);
			close(pollsock.fd);
			*reply_ip = htonl(ack_addr.sin_addr.s_addr);
			dhtLog("udpBroadcastWaitForResponse(): received response\n");
			return bytesReceived;
		}
	}
	close(pollsock.fd);
	dhtLog("udpBroadcastWaitForResponse(): timed out, no ack\n");
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

	dhtLog("tcpListen(): listening on port %d\n", TCP_PORT);

	while(1)
	{
		tcpAcceptSock = accept(tcpListenSock, (struct sockaddr *)&clientAddr,
			&socklen);
		pthread_create(&threadTcpAccept, NULL, tcpAccept, (void *)tcpAcceptSock);
	}
}

void *tcpAccept(void *arg)
{
	int tcpAcceptSock = (int)arg;
	int bytesReceived;
	char msgType;

	dhtLog("tcpAccept(): accepted tcp connection, file descriptor: %d\n",
		tcpAcceptSock);

	bytesReceived = recv(tcpAcceptSock, &msgType, sizeof(char), 0);
	if (bytesReceived == -1)
	{	perror("tcpAccept():recv()");	}
	else if (bytesReceived == 0)
	{
		dhtLog( "tcpAccept(): bytesReceived = 0\n", tcpAcceptSock);
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
				dhtLog("tcpAccept(): unrecognized msg type\n");
		}
	}

	if (close(tcpAcceptSock) == -1)
	{	perror("tcpAccept():close()"); }

	dhtLog("tcpAccept(): closed tcp connection, file descriptor: %d\n",
		tcpAcceptSock);

	pthread_exit(NULL);
}

unsigned int getKeyOwner(unsigned int key)
{
	if (state == NORMAL_STATE || state == LEAD_NORMAL_STATE
		|| state == REBUILD3_STATE || state == LEAD_REBUILD3_STATE)
	{
		return hostArray[blockOwnerArray[hash(key)]].ipAddr;
	}
	else
	{ //TODO: figure out what is best to do here. Would like calls to dhtSearch,
		// etc. to block rather than fail during rebuilds
		return 0;
	}
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

	dhtLog("findLeader(): broadcasting...\n");

	myMessage = FIND_LEADER_REQ;

	bytesReceived = udpBroadcastWaitForResponse(&reply_ip, UDP_PORT,
		(void *)&myMessage, sizeof(myMessage), (void *)&response,
		sizeof(response), TIMEOUT_MS, MAX_RETRIES);

	if (bytesReceived == -1)
	{
		dhtLog("findLeader(): no response\n");
		return 0;
	}
	else if (response == FIND_LEADER_RES)
	{
		struct in_addr reply_addr;
		reply_addr.s_addr = htonl(reply_ip);
		dhtLog("findLeader(): leader found:%s\n",
					inet_ntoa(reply_addr));
		return reply_ip;
	}
	else
	{
		dhtLog("findLeader(): unexpected response\n");
		return 0;
	}
}

int getDHTdata()
{
	struct sockaddr_in leader_addr;
	int sock;
	char msg;
	int bytesReceived;

	clearDHTdata();

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
		dhtLog("getDHTdata(): ERROR: numHosts not completely received\n");
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
		dhtLog("getDHTdata(): ERROR: numBlocks not completely received\n");
		close(sock);
		return -1;
	}
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
		dhtLog("getDHTdata(): ERROR: hostArray not completely received\n");
		close(sock);
		return -1;
	}
	blockOwnerArray = calloc(numBlocks, sizeof(unsigned int));
	bytesReceived = recv(sock,blockOwnerArray,numBlocks*sizeof(unsigned int),0);
	if (bytesReceived == -1)
	{
		perror("getDHTdata():recv()");
		close(sock);
		return -1;
	}
	if (bytesReceived != numBlocks*sizeof(unsigned int))
	{
		dhtLog("getDHTdata(): ERROR: blockOwnerArray not completely received\n");
		close(sock);
		return -1;
	}
	dhtLog("getDHTdata(): got data:\n");
	writeDHTdata(logfile);

	return 0;
}

unsigned int hash(unsigned int x)
{
	//this shouldn't be called when numBlocks = 0, so if you get a divide-by-zero,
	// make sure we are in a proper state for key owner lookups
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
		if (retry_count > 0)
		{
			dhtLog("initRebuild(): retry count:%d\n", retry_count);
		}

		if (leader == 0 || retry_count > 0)
		{
			leader = findLeader(); //broadcast
			if (leader == 0) //no response
			{
				//TODO:elect leader: this will do for now
				initDHTdata();
				leader = getMyIpAddr();
				state = LEAD_NORMAL_STATE;
			}
		}
	
		msg = REBUILD_REQ;

		bytesReceived = udpSendWaitForResponse(leader, UDP_PORT,
			(void *)&msg, sizeof(msg), (void *)&response, sizeof(response),
			TIMEOUT_MS, MAX_RETRIES);
		if (bytesReceived == -1)
		{	perror("initRebuild():recv()");	}
		else if (bytesReceived != sizeof(response))
		{
			dhtLog("initRebuild(): ERROR: response not completely received\n");
		}
		else if (response == NOT_LEADER)
		{
			struct in_addr address;
			address.s_addr = htonl(leader);
			dhtLog("initRebuild(): ERROR: %s no longer leader\n",
				inet_ntoa(address));
		}
		else if (response != REBUILD_RES)
		{
			dhtLog("initRebuild(): ERROR: unexpected response\n");
		}
		else
		{
			dhtLog("initRebuild(): submitted rebuild request\n");
			writeDHTdata(logfile);
			done = 1;
		}
	}
	return;
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
		fprintf(outfile,"%d: %d  ", i, blockOwnerArray[i]);
	fprintf(outfile,"\n");
}

void clearDHTdata()
{
	if (hostArray != NULL)
	{
		free(hostArray);
		hostArray = NULL;
	}
	if (blockOwnerArray != NULL)
	{
		free(blockOwnerArray);
		blockOwnerArray = NULL;
	}
	numHosts = numBlocks = hostArraySize = 0;
	return;
}

void initDHTdata()
{
	int i;

	clearDHTdata();
	hostArraySize = INIT_HOST_ALLOC;
	hostArray = calloc(hostArraySize, sizeof(struct hostData));
	numHosts = 1;
	hostArray[0] = myHostData;
	numBlocks = INIT_BLOCK_NUM;
	blockOwnerArray = calloc(numBlocks, sizeof(unsigned int));
	for (i = 0; i < numBlocks; i++)
		blockOwnerArray[i] = 0;
	
	return;
}

void addHost(struct hostData newHost)
{
	struct hostData *newArray;
	unsigned int newArraySize;

	if (hostArray == NULL || blockOwnerArray == NULL || hostArraySize == 0)
		initDHTdata();

	if (numHosts == hostArraySize)
	{
		newArraySize = hostArraySize * 2;
		newArray = calloc(newArraySize, sizeof(struct hostData));
		memcpy(newArray, hostArray, (hostArraySize * sizeof(struct hostData)));
		free(hostArray);
		hostArray = newArray;
		hostArraySize = newArraySize;
	}

	hostArray[numHosts] = newHost;
	numHosts++;

	return;
}

void makeAssignments()
{
	int i;

	if (hostArray == NULL || blockOwnerArray == NULL || hostArraySize == 0)
		initDHTdata();
	
	if (numBlocks < numHosts)
	{
		free(blockOwnerArray);
		while (numBlocks < numHosts)
			numBlocks *= 2;
		blockOwnerArray = calloc(numBlocks, sizeof(unsigned int));
	}

	for (i = 0; i < numBlocks; i++)
		blockOwnerArray[i]  = i % numHosts;

	return;
}

//returns not-zero if ok, zero if not ok
int msgSizeOk(unsigned char type, unsigned int size)
{
	int status;

	switch (type)
	{
		case INSERT_CMD:
			status = (size == sizeof(struct insertCmd));
			break;
		case INSERT_RES:
			status = (size == sizeof(struct insertRes));
			break;
		case REMOVE_CMD:
			status = (size == sizeof(struct removeCmd));
			break;
		case REMOVE_RES:
			status = (size == sizeof(struct removeRes));
			break;
		case SEARCH_CMD:
			status = (size == sizeof(struct searchCmd));
			break;
		case SEARCH_RES:
			status = (size == sizeof(struct searchRes));
			break;
		case FIND_LEADER_REQ:
			status = (size == sizeof(char));
			break;
		case FIND_LEADER_RES:
			status = (size == sizeof(char));
			break;
		case REBUILD_REQ:
			status = (size == sizeof(char));
			break;
		case REBUILD_RES:
			status = (size == sizeof(char));
			break;
		case NOT_LEADER:
			status = (size == sizeof(char));
			break;
		case REBUILD_CMD:
			status = (size == sizeof(char));
			break;
		case JOIN_REQ:
			status = (size == sizeof(struct joinReq));
			break;
		case JOIN_RES:
			status = (size == sizeof(char));
			break;
		case GET_DHT_INFO_CMD:
			status = (size == sizeof(char));
			break;
		case DHT_INFO_REQ:
			status = (size == sizeof(char));
			break;
		case DHT_INFO_RES:
			status = (size == sizeof(char));
			break;
		case FILL_DHT_CMD:
			status = (size == sizeof(char));
			break;
		case FILL_DHT_RES:
			status = (size == sizeof(char));
			break;
		case REBUILD_DONE_INFO:
			status = (size == sizeof(char));
			break;
		default:
			status = 0;
			break;
	}
	return status;
}

void dhtLog(const char *format, ...)
{
	va_list args;
	struct timeval now;

	if (gettimeofday(&now, NULL) < 0)
	{	perror("dhtLog():gettimeofday()"); }
	va_start(args, format);
	if (fprintf(logfile, "%d.%06d:", now.tv_sec, now.tv_usec) < 0)
	{	perror("dhtLog():fprintf()"); }
	if (vfprintf(logfile, format, args) < 0)
	{	perror("dhtLog():vfprintf()"); }
	if (fflush(logfile) == EOF)
	{	perror("dhtLog():fflush()"); }
	va_end(args);

	return;
}

#endif


