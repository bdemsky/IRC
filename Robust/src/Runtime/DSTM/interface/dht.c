/*******************************************************************************
*                                 dht.c
*
*  High-performance Distributed Hash Table for finding the location of objects
* in a Distributed Shared Transactional Memory system.
*
* Creator: Erik Rubow
*
* TODO:
* 1) Instead of having dhtInsertMult, dhtSearchMult, etc. call their single-key
* counterparts repeatedly, define some new messages to handle it more
* efficiently.
* 2) Improve the efficiency of functions that work with hostArray, hostReplied,
* and blockOwnerArray.
* 3) Currently a join or leave causes a rebuild of the entire hash table.
* Implement more graceful join and leave procedures.
* 4) Fine tune timeout values for performance, possibly implement a backoff
* algorithm to prevent overloading the network.
* 5) Whatever else I'm forgetting
*
*******************************************************************************/
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
#include <sys/queue.h>
#include "dht.h"
#include "clookup.h" //this works for now, do we need anything better?
#include "mlookup.h"

/*******************************************************************************
*                           Local Defines, Structs
*******************************************************************************/

#define MAX_MSG_SIZE 1500
#define UDP_PORT 2157
#define INIT_HOST_ALLOC 3
#define INIT_NUM_BLOCKS 16
#define DEFAULT_INTERFACE "eth0"
#define TIMEOUT_PERIOD 100
#define INSERT_TIMEOUT_MS 500
#define INSERT_RETRIES 50
#define REMOVE_TIMEOUT_MS 500
#define REMOVE_RETRIES 50
#define SEARCH_TIMEOUT_MS 500
#define SEARCH_RETRIES 50

//message types
//make sure this matches msg_types global var
enum
{
	INSERT_CMD,
	INSERT_RES,
	REMOVE_CMD,
	REMOVE_RES,
	SEARCH_CMD,
	SEARCH_RES,
	WHO_IS_LEADER_CMD,
	WHO_IS_LEADER_RES,
	JOIN_REQ,
	JOIN_RES,
	LEAVE_REQ,
	LEAVE_RES,
	DHT_UPDATE_CMD,
	DHT_UPDATE_RES,
	ELECT_LEADER_CMD,
	ELECT_LEADER_RES,
	CONGRATS_CMD,
	REBUILD_REQ,
	REBUILD_CMD,
	FILL_DHT_CMD,
	FILL_DHT_RES,
	RESUME_NORMAL_CMD,
	RESUME_NORMAL_RES,
	NUM_MSG_TYPES
};

//states
//make sure this matches state_names, timeout_vals, and retry_vals global vars
enum
{
	INIT1_STATE,
	INIT2_STATE,
	NORMAL_STATE,
	LEAD_NORMAL1_STATE,
	LEAD_NORMAL2_STATE,
	ELECT1_STATE,
	ELECT2_STATE,
	REBUILD0_STATE,
	REBUILD1_STATE,
	REBUILD2_STATE,
	REBUILD3_STATE,
	REBUILD4_STATE,
	REBUILD5_STATE,
	LEAD_REBUILD1_STATE,
	LEAD_REBUILD2_STATE,
	LEAD_REBUILD3_STATE,
	LEAD_REBUILD4_STATE,
	EXIT1_STATE,
	EXIT2_STATE,
	NUM_STATES
};

//status codes
enum
{
	OPERATION_OK,
	KEY_NOT_FOUND,
	NOT_KEY_OWNER,
	NOT_LEADER,
	INTERNAL_ERROR
};

struct hostData
{
	unsigned int ipAddr;
	unsigned int maxKeyCapacity;
};

/*******************************************************************************
*                         Local Function Prototypes
*******************************************************************************/

int msgSizeOk(unsigned char *msg, unsigned int size);
unsigned short read2(unsigned char *msg);
unsigned int read4(unsigned char *msg);
void write2(unsigned char *ptr, unsigned short tmp);
void write4(unsigned char *ptr, unsigned int tmp);
unsigned int getMyIpAddr(const char *interfaceStr);
int udpSend(unsigned char *msg, unsigned int size, unsigned int destIp);
int udpSendAll(unsigned char *msg, unsigned int size);
unsigned int hash(unsigned int x);
unsigned int getKeyOwner(unsigned int key);
void setState(unsigned int newState);
void makeAssignments();
int addHost(struct hostData newHost);
int removeHost(unsigned int ipAddr);
void removeUnresponsiveHosts();
int checkReplied(unsigned int ipAddr);
int allReplied();
void writeHostList();
void dhtLog(const char *format, ...);
void *fillTask();
void *udpListen();

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
	"WHO_IS_LEADER_CMD",
	"WHO_IS_LEADER_RES",
	"JOIN_REQ",
	"JOIN_RES",
	"LEAVE_REQ",
	"LEAVE_RES",
	"DHT_UPDATE_CMD",
	"DHT_UPDATE_RES",
	"ELECT_LEADER_CMD",
	"ELECT_LEADER_RES",
	"CONGRATS_CMD",
	"REBUILD_REQ",
	"REBUILD_CMD",
	"FILL_DHT_CMD",
	"FILL_DHT_RES",
	"RESUME_NORMAL_CMD",
	"RESUME_NORMAL_RES"
};

const char *state_names[NUM_STATES] =
{
	"INIT1_STATE",
	"INIT2_STATE",
	"NORMAL_STATE",
	"LEAD_NORMAL1_STATE",
	"LEAD_NORMAL2_STATE",
	"ELECT1_STATE",
	"ELECT2_STATE",
	"REBUILD0_STATE",
	"REBUILD1_STATE",
	"REBUILD2_STATE",
	"REBUILD3_STATE",
	"REBUILD4_STATE",
	"REBUILD5_STATE",
	"LEAD_REBUILD1_STATE",
	"LEAD_REBUILD2_STATE",
	"LEAD_REBUILD3_STATE",
	"LEAD_REBUILD4_STATE",
	"EXIT1_STATE",
	"EXIT2_STATE",
};

//note: { 0, 0 } means no timeout
struct timeval timeout_vals[NUM_STATES] =
{
	{ 0, 500000 }, //INIT1_STATE
	{ 0, 500000 }, //INIT2_STATE
	{ 0, 0 }, //NORMAL_STATE
	{ 0, 0 }, //LEAD_NORMAL1_STATE
	{ 3, 0 }, //LEAD_NORMAL2_STATE
	{ 1, 0 }, //ELECT1_STATE
	{ 1, 0 }, //ELECT2_STATE
	{ 0, 500000 }, //REBUILD0_STATE
	{ 0, 500000 }, //REBUILD1_STATE
	{ 10, 0 }, //REBUILD2_STATE
	{ 10, 0 }, //REBUILD3_STATE
	{ 10, 0 }, //REBUILD4_STATE
	{ 1, 0 }, //REBUILD5_STATE
	{ 1, 0 }, //LEAD_REBUILD1_STATE
	{ 1, 0 }, //LEAD_REBUILD2_STATE
	{ 10, 0 }, //LEAD_REBUILD3_STATE
	{ 10, 0 }, //LEAD_REBUILD4_STATE
	{ 0, 500000 }, //EXIT1_STATE
	{ 0, 0 } //EXIT2_STATE
};

int retry_vals[NUM_STATES] =
{
	100, //INIT1_STATE
	10, //INIT2_STATE
	0, //NORMAL_STATE
	0, //LEAD_NORMAL1_STATE
	0, //LEAD_NORMAL2_STATE
	10, //ELECT1_STATE
	10, //ELECT2_STATE
	10, //REBUILD0_STATE
	10, //REBUILD1_STATE
	0, //REBUILD2_STATE
	0, //REBUILD3_STATE
	0, //REBUILD4_STATE
	10, //REBUILD5_STATE
	10, //LEAD_REBUILD1_STATE
	10, //LEAD_REBUILD2_STATE
	10, //LEAD_REBUILD3_STATE
	10, //LEAD_REBUILD4_STATE
	10, //EXIT1_STATE
	0 //EXIT2_STATE
};

FILE *logfile;
struct hostData myHostData;
pthread_t threadUdpListen;
pthread_t threadFillTask;
//status of fillTask: 0 = ready to run, 1 = running, 2 = completed, 3 = error
int fillStatus;
struct pollfd udpPollSock;
unsigned int state;
unsigned int seed;
unsigned int leader;
unsigned int electionOriginator;
unsigned int electionParent;
unsigned int hostArraySize = 0;
struct hostData *hostArray = NULL;
unsigned int numBlocks = 0;
unsigned short *blockOwnerArray = NULL;
unsigned char *hostReplied = NULL;
pthread_mutex_t stateMutex;
pthread_cond_t stateCond;
chashtable_t *myHashTable;
unsigned int numHosts;
struct timeval timer;
int timerSet;
int timeoutCntr;

/*******************************************************************************
*                      Interface Function Definitions
*******************************************************************************/

void dhtInit(unsigned int seedIpAddr, unsigned int maxKeyCapacity)
{
	struct in_addr tmpAddr;
	char filename[23] = "dht-";
	struct sockaddr_in myAddr;
	struct sockaddr_in seedAddr;
	socklen_t socklen = sizeof(struct sockaddr_in);
	char initMsg;

	tmpAddr.s_addr = htonl(getMyIpAddr(DEFAULT_INTERFACE));
	strcat(filename, inet_ntoa(tmpAddr));
	strcat(filename, ".log");
	printf("log file: %s\n", filename);

	logfile = fopen(filename, "w");
	dhtLog("dhtInit(): inializing...\n");

	myHostData.ipAddr = getMyIpAddr(DEFAULT_INTERFACE);
	myHostData.maxKeyCapacity = maxKeyCapacity;

	seed = seedIpAddr;
	leader = 0;
	electionOriginator = 0;
	electionParent = 0;
	hostArraySize = INIT_HOST_ALLOC;
	hostArray = calloc(hostArraySize, sizeof(struct hostData));
	hostReplied = calloc(hostArraySize, sizeof(unsigned char));
	hostArray[0] = myHostData;
	numHosts = 1;
	numBlocks = INIT_NUM_BLOCKS;
	blockOwnerArray = calloc(numBlocks, sizeof(unsigned short));
	pthread_mutex_init(&stateMutex, NULL);
	pthread_cond_init(&stateCond, NULL);
	myHashTable = chashCreate(HASH_SIZE, LOADFACTOR);

	udpPollSock.fd = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
	if (udpPollSock.fd < 0)
		perror("dhtInit():socket()");

	udpPollSock.events = POLLIN;
	
	bzero(&myAddr, socklen);
	myAddr.sin_family = AF_INET;
	myAddr.sin_addr.s_addr = htonl(INADDR_ANY);
	myAddr.sin_port = htons(UDP_PORT);

	if (bind(udpPollSock.fd, (struct sockaddr *)&myAddr, socklen) < 0)
		perror("dhtInit():bind()");

	if (seed == 0)
	{
		dhtLog("I am the leader\n");
		leader = myHostData.ipAddr;
		setState(LEAD_NORMAL1_STATE);
	}
	else
	{
		initMsg = WHO_IS_LEADER_CMD;
		udpSend(&initMsg, 1, seed);
		setState(INIT1_STATE);
	}

	if (pthread_create(&threadUdpListen, NULL, udpListen, NULL) != 0)
		dhtLog("dhtInit() - ERROR creating threadUdpListen\n");

	return;
}

void dhtExit()
{ //TODO: do this gracefully, wait for response from leader, etc.
	char msg;

	msg = LEAVE_REQ;
	udpSend(&msg, 1, leader);
	dhtLog("dhtExit(): cleaning up...\n");
	pthread_cancel(threadUdpListen);
	close(udpPollSock.fd);
	free(hostArray);
	free(hostReplied);
	free(blockOwnerArray);
	fclose(logfile);

	return;
}

int dhtInsert(unsigned int key, unsigned int val)
{
	struct sockaddr_in toAddr;
	struct sockaddr_in fromAddr;
	socklen_t socklen = sizeof(struct sockaddr_in);
	struct pollfd pollsock;
	char inBuffer[2];
	char outBuffer[9];
	ssize_t bytesRcvd;
	int i;
	int retval;
	int status = -1;

	bzero((char *)&toAddr, socklen);
	toAddr.sin_family = AF_INET;
	toAddr.sin_port = htons(UDP_PORT);

	while (status != OPERATION_OK)
	{
		pthread_mutex_lock(&stateMutex);
		while (!(state == NORMAL_STATE || state == LEAD_NORMAL1_STATE
				|| state == LEAD_NORMAL2_STATE || state == REBUILD4_STATE
				|| state == LEAD_REBUILD3_STATE))
			pthread_cond_wait(&stateCond, &stateMutex);
		toAddr.sin_addr.s_addr = htonl(getKeyOwner(key));
		pthread_mutex_unlock(&stateMutex);

		if ((pollsock.fd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
		{
			perror("dhtInsert():socket()");
			return -1;
		}
		pollsock.events = POLLIN;

		outBuffer[0] = INSERT_CMD;
		write4(&outBuffer[1], key);
		write4(&outBuffer[5], val);

		for (i = 0; i < INSERT_RETRIES; i++)
		{
			if (sendto(pollsock.fd, outBuffer, 9, 0, (struct sockaddr *)&toAddr,
				socklen) < 0)
			{
				perror("dhtInsert():sendto()");
				break;
			}
			retval = poll(&pollsock, 1, INSERT_TIMEOUT_MS);
			if (retval < 0)
			{
				perror("dhtInsert():poll()");
				break;
			}
			if (retval > 0)
			{
				bytesRcvd = recvfrom(pollsock.fd, inBuffer, 2, 0,
					(struct sockaddr *)&fromAddr, &socklen);
				if (fromAddr.sin_addr.s_addr == toAddr.sin_addr.s_addr
					&& fromAddr.sin_port == toAddr.sin_port
					&& bytesRcvd == 2 && inBuffer[0] == INSERT_RES)
				{
					status = inBuffer[1]; //status from remote host
					break;
				}
			}
		}
		if (status != OPERATION_OK)
		{
			pthread_mutex_lock(&stateMutex);
			setState(REBUILD0_STATE);
			outBuffer[0] = REBUILD_REQ;
			udpSend(outBuffer, 1, leader);
			pthread_mutex_unlock(&stateMutex);
		}
	}

	close(pollsock.fd);

	return status;
}

int dhtInsertMult(unsigned int numKeys, unsigned int *keys,	unsigned int *vals)
{
	int status;
	int i;

	status = 0;
	for (i = 0; i < numKeys; i++)
	{
		if (dhtInsert(keys[i], vals[i]) != 0)
			status = -1;
	}
	return status;
}

int dhtRemove(unsigned int key)
{
	struct sockaddr_in toAddr;
	struct sockaddr_in fromAddr;
	socklen_t socklen = sizeof(struct sockaddr_in);
	struct pollfd pollsock;
	char inBuffer[2];
	char outBuffer[5];
	ssize_t bytesRcvd;
	int i;
	int retval;
	int status = -1;

	bzero((char *)&toAddr, socklen);
	toAddr.sin_family = AF_INET;
	toAddr.sin_port = htons(UDP_PORT);

	while (!(status == OPERATION_OK || status == KEY_NOT_FOUND))
	{
		pthread_mutex_lock(&stateMutex);
		while (!(state == NORMAL_STATE || state == LEAD_NORMAL1_STATE
				|| state == LEAD_NORMAL2_STATE))
			pthread_cond_wait(&stateCond, &stateMutex);
		toAddr.sin_addr.s_addr = htonl(getKeyOwner(key));
		pthread_mutex_unlock(&stateMutex);

		if ((pollsock.fd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
		{
			perror("dhtRemove():socket()");
			return -1;
		}
		pollsock.events = POLLIN;

		outBuffer[0] = REMOVE_CMD;
		write4(&outBuffer[1], key);

		for (i = 0; i < REMOVE_RETRIES; i++)
		{
			if (sendto(pollsock.fd, outBuffer, 5, 0, (struct sockaddr *)&toAddr,
				socklen) < 0)
			{
				perror("dhtRemove():sendto()");
				break;
			}
			retval = poll(&pollsock, 1, REMOVE_TIMEOUT_MS);
			if (retval < 0)
			{
				perror("dhtRemove():poll()");
				break;
			}
			if (retval > 0)
			{
				bytesRcvd = recvfrom(pollsock.fd, inBuffer, 2, 0,
					(struct sockaddr *)&fromAddr, &socklen);
				if (fromAddr.sin_addr.s_addr == toAddr.sin_addr.s_addr
					&& fromAddr.sin_port == toAddr.sin_port
					&& bytesRcvd == 2 && inBuffer[0] == REMOVE_RES)
				{
					status = inBuffer[1]; //status from remote host
					break;
				}
			}
		}
		if (!(status == OPERATION_OK || status == KEY_NOT_FOUND))
		{
			pthread_mutex_lock(&stateMutex);
			setState(REBUILD0_STATE);
			outBuffer[0] = REBUILD_REQ;
			udpSend(outBuffer, 1, leader);
			pthread_mutex_unlock(&stateMutex);
		}
	}

	close(pollsock.fd);

	return status;
}

int dhtRemoveMult(unsigned int numKeys, unsigned int *keys)
{
	int status;
	int i;

	status = 0;
	for (i = 0; i < numKeys; i++)
	{
		if (dhtRemove(keys[i]) != 0)
			status = -1;
	}
	return status;
}

int dhtSearch(unsigned int key, unsigned int *val)
{
	struct sockaddr_in toAddr;
	struct sockaddr_in fromAddr;
	socklen_t socklen = sizeof(struct sockaddr_in);
	struct pollfd pollsock;
	char inBuffer[6];
	char outBuffer[5];
	ssize_t bytesRcvd;
	int i;
	int retval;
	int status = -1;

	bzero((char *)&toAddr, socklen);
	toAddr.sin_family = AF_INET;
	toAddr.sin_port = htons(UDP_PORT);

	while (!(status == OPERATION_OK || status == KEY_NOT_FOUND))
	{
		pthread_mutex_lock(&stateMutex);
		while (numBlocks == 0)
			pthread_cond_wait(&stateCond, &stateMutex);
		toAddr.sin_addr.s_addr = htonl(getKeyOwner(key));
		pthread_mutex_unlock(&stateMutex);

		if ((pollsock.fd = socket(AF_INET, SOCK_DGRAM, 0)) < 0)
		{
			perror("dhtSearch():socket()");
			return -1;
		}
		pollsock.events = POLLIN;

		outBuffer[0] = SEARCH_CMD;
		write4(&outBuffer[1], key);

		for (i = 0; i < SEARCH_RETRIES; i++)
		{
			if (sendto(pollsock.fd, outBuffer, 5, 0, (struct sockaddr *)&toAddr,
				socklen) < 0)
			{
				perror("dhtSearch():sendto()");
				break;
			}
			retval = poll(&pollsock, 1, SEARCH_TIMEOUT_MS);
			if (retval < 0)
			{
				perror("dhtSearch():poll()");
				break;
			}
			if (retval > 0)
			{
				bytesRcvd = recvfrom(pollsock.fd, inBuffer, 6, 0,
					(struct sockaddr *)&fromAddr, &socklen);
				if (fromAddr.sin_addr.s_addr == toAddr.sin_addr.s_addr
					&& fromAddr.sin_port == toAddr.sin_port
					&& bytesRcvd == 6 && inBuffer[0] == SEARCH_RES)
				{
					status = inBuffer[1]; //status from remote host
					*val = read4(&inBuffer[2]);
					break;
				}
			}
		}
		if (!(status == OPERATION_OK || status == KEY_NOT_FOUND))
		{
			pthread_mutex_lock(&stateMutex);
			setState(REBUILD0_STATE);
			outBuffer[0] = REBUILD_REQ;
			udpSend(outBuffer, 1, leader);
			pthread_mutex_unlock(&stateMutex);
		}
	}

	close(pollsock.fd);

	return status;
}

int dhtSearchMult(unsigned int numKeys, unsigned int *keys, unsigned int *vals)
{
	int i;
	int status = 0;
	for (i = 0; i < numKeys; i++)
	{
		if (dhtSearch(keys[i], &vals[i]) != 0)
			status = -1;
	}
	return status;
}

/*******************************************************************************
*                      Local Function Definitions
*******************************************************************************/

int msgSizeOk(unsigned char *msg, unsigned int size)
{
	unsigned short tmpNumHosts;
	unsigned short tmpNumBlocks;

	if (size < 1)
		return 1;

	switch (msg[0])
	{
		case WHO_IS_LEADER_CMD:
		case LEAVE_REQ:
		case LEAVE_RES:
		case DHT_UPDATE_RES:
		case REBUILD_REQ:
		case REBUILD_CMD:
		case FILL_DHT_CMD:
		case FILL_DHT_RES:
		case RESUME_NORMAL_CMD:
		case RESUME_NORMAL_RES:
			return (size == 1);
		case INSERT_RES:
		case REMOVE_RES:
		case JOIN_RES:
			return (size == 2);
		case REMOVE_CMD:
		case SEARCH_CMD:
		case WHO_IS_LEADER_RES:
		case JOIN_REQ:
		case ELECT_LEADER_CMD:
			return (size == 5);
		case SEARCH_RES:
			return (size == 6);
		case INSERT_CMD:
			return (size == 9);
		case DHT_UPDATE_CMD:
			if (size < 5)
				return 1;
			tmpNumHosts = read2(&msg[1]);
			tmpNumBlocks = read2(&msg[3]);
			return (size == (5+sizeof(struct hostData)*tmpNumHosts+2*tmpNumBlocks));
		case ELECT_LEADER_RES:
			if (size < 2)
				return 1;
			if (msg[1] == 0xFF)
				return (size == 2);
			if (size < 4)
				return 1;
			tmpNumHosts = read2(&msg[2]);
			return (size == (4 + sizeof(struct hostData) * tmpNumHosts));
		case CONGRATS_CMD:
			if (size < 3)
				return 1;
			tmpNumHosts = read2(&msg[1]);
			return (size == (3 + sizeof(struct hostData) * tmpNumHosts));
		default:
			return 1;
	}
}

unsigned short read2(unsigned char *ptr)
{
	unsigned short tmp = (ptr[1] << 8) | ptr[0];
	return tmp;
}

unsigned int read4(unsigned char *ptr)
{
	unsigned int tmp = (ptr[3] << 24) | (ptr[2] << 16) | (ptr[1] << 8) | ptr[0];
	return tmp;
}

void write2(unsigned char *ptr, unsigned short tmp)
{
	ptr[1] = (tmp >> 8) & 0xFF;
	ptr[0] = tmp & 0xFF;
	return;
}

void write4(unsigned char *ptr, unsigned int tmp)
{
	ptr[3] = (tmp >> 24) & 0xFF;
	ptr[2] = (tmp >> 16) & 0xFF;
	ptr[1] = (tmp >> 8) & 0xFF;
	ptr[0] = tmp & 0xFF;
	return;
}

unsigned int getMyIpAddr(const char *interfaceStr)
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

	strcpy(interfaceInfo.ifr_name, interfaceStr);
	myAddr->sin_family = AF_INET;
	
	if(ioctl(sock, SIOCGIFADDR, &interfaceInfo) != 0)
	{
		perror("getMyIpAddr():ioctl()");
		return 1;
	}

	return ntohl(myAddr->sin_addr.s_addr);
}

int udpSend(unsigned char *msg, unsigned int size, unsigned int destIp)
{
	struct sockaddr_in peerAddr;
	socklen_t socklen = sizeof(struct sockaddr_in);

	bzero(&peerAddr, socklen);
	peerAddr.sin_family = AF_INET;
	peerAddr.sin_addr.s_addr = htonl(destIp);
	peerAddr.sin_port = htons(UDP_PORT);

	if (size >= 1)
	{
		if (msg[0] < NUM_MSG_TYPES)
			dhtLog("udpSend(): sending %s to %s, %d bytes\n", msg_types[msg[0]],
				inet_ntoa(peerAddr.sin_addr), size);
		else
			dhtLog("udpSend(): sending unknown message to %s, %d bytes\n",
				inet_ntoa(peerAddr.sin_addr), size);
	}

	if (sendto(udpPollSock.fd, (void *)msg, size, 0, (struct sockaddr *)&peerAddr,
			socklen) < 0)
	{
		perror("udpSend():sendto()");
		return -1;
	}
	
	return 0;
}

int udpSendAll(unsigned char *msg, unsigned int size)
{
	int i;
	int status = 0;
	for (i = 0; i < numHosts; i++)
	{
		if ((hostReplied[i] == 0) && (hostArray[i].ipAddr != myHostData.ipAddr))
		{
			if (udpSend(msg, size, hostArray[i].ipAddr) != 0)
				status = -1;
		}
	}
	return status;
}

//note: make sure this is only executed in a valid state, where numBlocks != 0
unsigned int hash(unsigned int x)
{
	return (x % numBlocks);
}

//note: make sure this is only executed in a valid state, where these arrays
// are allocated and the index mappings are consistent
unsigned int getKeyOwner(unsigned int key)
{
	return hostArray[blockOwnerArray[hash(key)]].ipAddr;
}

//sets state and timer, if applicable
void setState(unsigned int newState)
{
	struct timeval now;
	int i;
	
	gettimeofday(&now, NULL);
	
	if (newState >= NUM_STATES)
	{
		dhtLog("setState(): ERROR: invalid state %d\n", newState);
	}
	else
	{
		if (timeout_vals[newState].tv_sec == 0
			&& timeout_vals[newState].tv_usec == 0)
		{ //no timer
			timerSet = 0;
		}
		else
		{
			timeradd(&now, &timeout_vals[newState], &timer);
			timerSet = 1;
		}
		timeoutCntr = 0;
		state = newState;
		//TODO: only do this for states that require it
		for (i = 0; i < numHosts; i++)
			hostReplied[i] = 0;

		dhtLog("setState(): state set to %s\n", state_names[state]);
	}

	return;
}

//TODO: improve these simple and inefficient functions
int checkReplied(unsigned int ipAddr)
{
	int i;

	i = findHost(ipAddr);

	if (i == -1)
		return -1;

	hostReplied[i] = 1;

	return 0;
}

int allReplied()
{
	int i;

	for (i = 0; i < numHosts; i++)
		if ((hostReplied[i] == 0) && (hostArray[i].ipAddr != myHostData.ipAddr))
			return 0;
	
	return 1;
}

int findHost(unsigned int ipAddr)
{
	int i;

	for (i = 0; i < numHosts; i++)
		if (hostArray[i].ipAddr == ipAddr)
			return i; //found, return index
	
	return -1; //not found
}

int removeHost(unsigned int ipAddr)
{
	int i, j;

	i = findHost(ipAddr);

	if (i == -1)
		return -1;

	for (j = 0; j < numBlocks; j++)
	{
		if (blockOwnerArray[j] == i)
			blockOwnerArray[j] = 0; //TODO: is this what I want to have happen?
		else if (blockOwnerArray[j] > i)
			blockOwnerArray[j]--;
	}

	for (; i < numHosts - 1; i++)
	{
		hostArray[i] = hostArray[i+1];
		hostReplied[i] = hostReplied[i+1];
	}
	numHosts--;

	return 0;
}

void removeUnresponsiveHosts()
{
	int i;

	for (i = 0; i < numHosts; i++)
	{
		if (!hostReplied[i] && hostArray[i].ipAddr != myHostData.ipAddr)
			removeHost(hostArray[i].ipAddr);
	}
}

int addHost(struct hostData newHost)
{
	struct hostData *newHostArray;
	unsigned char *newHostReplied;
	int i;
	int j;

	for (i = 0; i < numHosts; i++)
	{
		if (hostArray[i].ipAddr == newHost.ipAddr)
		{
			hostArray[i] = newHost;
			hostReplied[i] = 0;
			return 0;
		}
		else if (hostArray[i].ipAddr > newHost.ipAddr)
		{
			if (numHosts == hostArraySize)
			{
				newHostArray = calloc(2 * hostArraySize, sizeof(struct hostData));
				newHostReplied = calloc(2 * hostArraySize, sizeof(unsigned char));
				memcpy(newHostArray, hostArray, (i * sizeof(struct hostData)));
				memcpy(newHostReplied, hostReplied, (i * sizeof(unsigned char)));
				newHostArray[i] = newHost;
				newHostReplied[i] = 0;
				memcpy(&newHostArray[i+1], &hostArray[i], ((numHosts - i) *
					sizeof(struct hostData)));
				memcpy(&newHostReplied[i+1], &hostReplied[i], ((numHosts - i) *
					sizeof(unsigned char)));
				free(hostArray);
				free(hostReplied);
				hostArray = newHostArray;
				hostReplied = newHostReplied;
				hostArraySize = 2 * hostArraySize;
			}
			else
			{
				for (j = numHosts; j > i; j--)
				{
					hostArray[j] = hostArray[j-1];
					hostReplied[j] = hostReplied[j-1];
				}
				hostArray[i] = newHost;
				hostReplied[i] = 0;
			}
			for(j = 0; j < numBlocks; j++)
			{
				if (blockOwnerArray[j] >= i)
					blockOwnerArray[j]++;
			}
			numHosts++;
			return 1;
		}
	}

	//nothing greater, add to end
	if (numHosts == hostArraySize)
	{
		newHostArray = calloc(2 * hostArraySize, sizeof(struct hostData));
		newHostReplied = calloc(2 * hostArraySize, sizeof(unsigned char));
		memcpy(newHostArray, hostArray, (numHosts * sizeof(struct hostData)));
		memcpy(newHostReplied, hostReplied, (numHosts * sizeof(unsigned char)));
		free(hostArray);
		free(hostReplied);
		hostArray = newHostArray;
		hostReplied = newHostReplied;
		hostArraySize = 2 * hostArraySize;
	}

	hostArray[numHosts] = newHost;
	hostReplied[numHosts] = 0;
	numHosts++;
	return 1;
}

void makeAssignments()
{
	int i;

	if (numBlocks < numHosts)
	{
		free(blockOwnerArray);
		while (numBlocks < numHosts)
			numBlocks *= 2;
		blockOwnerArray = calloc(numBlocks, sizeof(unsigned short));
	}

	for (i = 0; i < numBlocks; i++)
		blockOwnerArray[i]  = i % numHosts;

	return;
}

void writeHostList()
{
	int i;
	struct in_addr tmpAddr;

	fprintf(logfile, "numHosts = %d\n", numHosts);
	for (i = 0; i < numHosts; i++)
	{
		tmpAddr.s_addr = htonl(hostArray[i].ipAddr);
		fprintf(logfile, "%d) %s, %d\n", i, inet_ntoa(tmpAddr),
			hostArray[i].maxKeyCapacity);
	}
	return;
}

void dhtLog(const char *format, ...)
{
	va_list args;
//	struct timeval now;

//	if (gettimeofday(&now, NULL) < 0)
//	{	perror("dhtLog():gettimeofday()"); }
	va_start(args, format);
//	if (fprintf(logfile, "%d.%06d:", now.tv_sec, now.tv_usec) < 0)
//	{	perror("dhtLog():fprintf()"); }
	if (vfprintf(logfile, format, args) < 0)
	{	perror("dhtLog():vfprintf()"); }
	if (fflush(logfile) == EOF)
	{	perror("dhtLog():fflush()"); }
	va_end(args);

	return;
}

void *fillTask()
{
	unsigned int *vals;
	unsigned int *keys;
	unsigned int numKeys;
	int i;
	
	vals = mhashGetKeys(&numKeys); //note: key of mhash is val of dht
	keys = calloc(numKeys, sizeof(unsigned int));

	for (i = 0; i < numKeys; i++)
		keys[i] = myHostData.ipAddr;

	if (dhtInsertMult(numKeys, keys, vals) == 0)
		fillStatus = 2;
	else
		fillStatus = 3;
	
	pthread_exit(NULL);
}

void *udpListen()
{
	ssize_t bytesRcvd;
	struct sockaddr_in peerAddr;
	unsigned int peerIp;
	socklen_t socklen = sizeof(struct sockaddr_in);
	unsigned char inBuffer[MAX_MSG_SIZE];
	unsigned char outBuffer[MAX_MSG_SIZE];
	int pollret;
	struct timeval now;
	struct in_addr tmpAddr;
	struct hostData tmpHost;
	unsigned int tmpKey;
	unsigned int tmpVal;
	struct hostData *hostDataPtr;
	unsigned short *uShortPtr;
	unsigned int tmpUInt;
	unsigned int tmpUShort;
	int i;
	unsigned int oldState;

	dhtLog("udpListen(): linstening on port %d...\n", UDP_PORT);

	while (1)
	{
		pollret = poll(&udpPollSock, 1, TIMEOUT_PERIOD);
		pthread_mutex_lock(&stateMutex);
		oldState = state;
		if (pollret < 0)
		{
			perror("udpListen():poll()");
		}
		else if (pollret > 0)
		{
			bytesRcvd = recvfrom(udpPollSock.fd, inBuffer, MAX_MSG_SIZE, 0,
				(struct sockaddr *)&peerAddr, &socklen);
			if (bytesRcvd < 1)
			{
				dhtLog("udpListen(): ERROR: bytesRcvd = %d\n", bytesRcvd);
			}
			else if (inBuffer[0] >= NUM_MSG_TYPES)
			{
				dhtLog("udpListen(): ERROR: unknown msg type = %d\n", inBuffer[0]);
			}
			else if (!msgSizeOk(inBuffer, bytesRcvd))
			{
				dhtLog("udpListen(): ERROR: msg size not ok: type = %s\n, size = %d\n",
					msg_types[inBuffer[0]], bytesRcvd);
			}
			else if (state == EXIT2_STATE)
			{
				//do nothing
			}
			else if (state == INIT1_STATE)
			{ //after initialization with seed, do not proceed until seed replies
				dhtLog("udpListen(): received %s from %s, %d bytes\n",
					msg_types[inBuffer[0]], inet_ntoa(peerAddr.sin_addr), bytesRcvd);
				for (i = 0; i < bytesRcvd; i++)
					dhtLog(" %x", inBuffer[i]);
				dhtLog("\n");
				peerIp = ntohl(peerAddr.sin_addr.s_addr);
				if (peerIp == seed && inBuffer[0] == WHO_IS_LEADER_RES)
				{
					tmpHost.ipAddr = peerIp;
					tmpHost.maxKeyCapacity = 0;
					addHost(tmpHost);
					writeHostList();
					leader = read4(&inBuffer[1]);
					tmpAddr.s_addr = htonl(leader);
					dhtLog("leader = %s\n", inet_ntoa(tmpAddr));
					if (leader != 0)
					{
						setState(INIT2_STATE);
						outBuffer[0] = JOIN_REQ;
						write4(&outBuffer[1], myHostData.maxKeyCapacity);
						udpSend(outBuffer, 5, leader);
					}
					else
					{
						electionOriginator = myHostData.ipAddr;
						setState(ELECT1_STATE);
						outBuffer[0] = ELECT_LEADER_CMD;
						write4(&outBuffer[1], myHostData.ipAddr); //originator = me
						udpSendAll(outBuffer, 5);
					}
				}
			}
			else
			{
				dhtLog("udpListen(): received %s from %s, %d bytes\n",
					msg_types[inBuffer[0]], inet_ntoa(peerAddr.sin_addr), bytesRcvd);
				for (i = 0; i < bytesRcvd; i++)
					dhtLog(" %x", inBuffer[i]);
				dhtLog("\n");
				peerIp = ntohl(peerAddr.sin_addr.s_addr);
				switch (inBuffer[0])
				{
					case INSERT_CMD:
						if (state == NORMAL_STATE || state == LEAD_NORMAL1_STATE
							|| state == LEAD_NORMAL2_STATE || state == REBUILD4_STATE
							|| state == REBUILD5_STATE || state == LEAD_REBUILD3_STATE)
						{
							tmpKey = read4(&inBuffer[1]);
							tmpVal = read4(&inBuffer[5]);
							outBuffer[0] = INSERT_RES;
							if (getKeyOwner(tmpKey) == myHostData.ipAddr)
							{
								if (chashInsert(myHashTable, tmpKey, (void *)tmpVal) == 0)
									outBuffer[1] = OPERATION_OK;
								else
									outBuffer[1] = INTERNAL_ERROR;
							}
							else
							{
								outBuffer[1] = NOT_KEY_OWNER;
							}
							//reply to client socket
							sendto(udpPollSock.fd, outBuffer, 2, 0,
								(struct sockaddr *)&peerAddr, socklen);
						}
						break;
					case REMOVE_CMD:
						if (state == NORMAL_STATE || state == LEAD_NORMAL1_STATE
							|| state == LEAD_NORMAL2_STATE)
						{
							tmpKey = read4(&inBuffer[1]);
							outBuffer[0] = REMOVE_RES;
							if (getKeyOwner(tmpKey) == myHostData.ipAddr)
							{
								if (chashRemove(myHashTable, tmpKey) == 0)
									outBuffer[1] = OPERATION_OK;
								else
									outBuffer[1] = KEY_NOT_FOUND;
							}
							else
							{
								outBuffer[1] = NOT_KEY_OWNER;
							}
							//reply to client socket
							sendto(udpPollSock.fd, outBuffer, 2, 0,
								(struct sockaddr *)&peerAddr, socklen);
						}
						break;
					case SEARCH_CMD:
						if (state == NORMAL_STATE || state == LEAD_NORMAL1_STATE
							|| state == LEAD_NORMAL2_STATE)
						{
							tmpKey = read4(&inBuffer[1]);
							outBuffer[0] = SEARCH_RES;
							if (getKeyOwner(tmpKey) == myHostData.ipAddr)
							{
								if ((tmpVal = (unsigned int)chashSearch(myHashTable, tmpKey)) != 0)
								{
									outBuffer[1] = OPERATION_OK;
									write4(&outBuffer[2], tmpVal);
								}
								else
								{
									outBuffer[1] = KEY_NOT_FOUND;
									write4(&outBuffer[2], 0);
								}
							}
							else
							{
								outBuffer[1] = NOT_KEY_OWNER;
								write4(&outBuffer[2], 0);
							}
							//reply to client socket
							sendto(udpPollSock.fd, outBuffer, 6, 0,
								(struct sockaddr *)&peerAddr, socklen);
						}
						break;
					case WHO_IS_LEADER_CMD:
						tmpHost.ipAddr = peerIp;
						tmpHost.maxKeyCapacity = 0;
						addHost(tmpHost);
						writeHostList();
						outBuffer[0] = WHO_IS_LEADER_RES;
						//leader == 0 means I don't know who it is
						write4(&outBuffer[1], leader);
						udpSend(outBuffer, 5, peerIp);
						break;
					case JOIN_REQ:
						if (state == LEAD_NORMAL1_STATE || state == LEAD_NORMAL2_STATE)
						{
							tmpHost.ipAddr = peerIp;
							tmpHost.maxKeyCapacity = read4(&inBuffer[1]);
							addHost(tmpHost);
							writeHostList();
							if (state == LEAD_NORMAL1_STATE)
								setState(LEAD_NORMAL2_STATE);
							outBuffer[0] = JOIN_RES;
							outBuffer[1] = 0; //status, success
							udpSend(outBuffer, 2, peerIp);
						}
						else if (state == LEAD_REBUILD1_STATE)
						{
							//note: I don't need to addHost().
							checkReplied(peerIp);
							outBuffer[0] = JOIN_RES;
							outBuffer[1] = 0; //status, success
							udpSend(outBuffer, 2, peerIp);
							if (allReplied())
							{
								makeAssignments();
								setState(LEAD_REBUILD2_STATE);
								outBuffer[0] = DHT_UPDATE_CMD;
								write2(&outBuffer[1], numHosts);
								write2(&outBuffer[3], numBlocks);
								memcpy(&outBuffer[5], hostArray, numHosts*sizeof(struct hostData));
								memcpy(&outBuffer[5+numHosts*sizeof(struct hostData)],
									blockOwnerArray, numBlocks*2);
								udpSendAll(outBuffer, 5 + sizeof(struct hostData) * numHosts
									+ 2 * numBlocks);
							}
						}
						break;
					case JOIN_RES:
						if (state == REBUILD1_STATE)
						{
							setState(REBUILD2_STATE);
						}
						else if (state == INIT2_STATE)
						{
							setState(NORMAL_STATE);
						}
						break;
					case LEAVE_REQ:
						if (state == LEAD_NORMAL1_STATE || state == LEAD_NORMAL2_STATE)
						{ //TODO: make this graceful, instead of just rebuilding
							removeHost(peerIp);
							if (state != LEAD_NORMAL2_STATE)
								setState(LEAD_NORMAL2_STATE);
						}
						break;
					case DHT_UPDATE_CMD:
						if (state == REBUILD2_STATE && peerIp == leader)
						{
							free(hostArray);
							free(blockOwnerArray);
							numHosts = read2(&inBuffer[1]);
							numBlocks = read2(&inBuffer[3]);
							while (hostArraySize < numHosts)
								hostArraySize *= 2;
							hostArray = calloc(hostArraySize, sizeof(struct hostData));
							blockOwnerArray = calloc(numBlocks, 2);
							memcpy(hostArray, &inBuffer[5], numHosts*sizeof(struct hostData));
							memcpy(blockOwnerArray, &inBuffer[5+numHosts*sizeof(struct hostData)], numBlocks*2);
							writeHostList();
							setState(REBUILD3_STATE);
							outBuffer[0] = DHT_UPDATE_RES;
							udpSend(outBuffer, 1, peerIp);
						}
						break;
					case DHT_UPDATE_RES:
						if (state == LEAD_REBUILD2_STATE)
						{
							checkReplied(peerIp);
							if (allReplied())
							{
								setState(LEAD_REBUILD3_STATE);
								outBuffer[0] = FILL_DHT_CMD;
								udpSendAll(outBuffer, 1);
								if (fillStatus != 0)
									dhtLog("udpListen(): ERROR: fillTask already running\n");
								fillStatus = 1;
								if (pthread_create(&threadFillTask, NULL, fillTask, NULL) != 0)
									dhtLog("udpListen(): ERROR creating threadFillTask\n");
							}
						}
						break;
					case ELECT_LEADER_CMD:
						tmpUInt = read4(&inBuffer[1]);
						if ((state == ELECT1_STATE || state == ELECT2_STATE)
							&& tmpUInt >= electionOriginator)
						{ //already participating in a higher-priority election
							outBuffer[0] = ELECT_LEADER_RES;
							outBuffer[1] = 0xFF;
							udpSend(outBuffer, 2, peerIp);
						}
						else
						{ //join election
							electionOriginator = tmpUInt;
							electionParent = peerIp;
							setState(ELECT1_STATE);
							outBuffer[0] = ELECT_LEADER_CMD;
							write4(&outBuffer[1], electionOriginator);
							//don't bother forwarding the message to originator or parent
							checkReplied(electionOriginator);
							checkReplied(electionParent);
							if (allReplied())
							{ //in case that is everybody I know of
								setState(ELECT2_STATE);
								outBuffer[0] = ELECT_LEADER_RES;
								outBuffer[1] = 0;
								write2(&outBuffer[2], numHosts);
								memcpy(&outBuffer[4], hostArray, sizeof(struct hostData)
									* numHosts);
								udpSend(outBuffer, 4 + sizeof(struct hostData) * numHosts,
									electionParent);
							}
							else
							{
								udpSendAll(outBuffer, 5);
							}
						}
						break;
					case ELECT_LEADER_RES:
						if (state == ELECT1_STATE)
						{
							checkReplied(peerIp);
							if (inBuffer[1] != 0xFF)
							{
								tmpUShort = read2(&inBuffer[2]);
								hostDataPtr = (struct hostData *)&inBuffer[4];
								for (i = 0; i < tmpUShort; i++)
									addHost(hostDataPtr[i]);
								writeHostList();
							}
							if (allReplied())
							{
								setState(ELECT2_STATE);
								if (electionOriginator == myHostData.ipAddr)
								{
									leader = hostArray[0].ipAddr;
									if (leader == myHostData.ipAddr)
									{ //I am the leader
										dhtLog("I am the leader!\n");
										setState(LEAD_REBUILD1_STATE);
										outBuffer[0] = REBUILD_CMD;
										udpSendAll(outBuffer, 1);
									}
									else
									{ //notify leader
										outBuffer[0] = CONGRATS_CMD;
										write2(&outBuffer[1], numHosts);
										hostDataPtr = (struct hostData *)&outBuffer[3];
										for (i = 0; i < numHosts; i++)
											hostDataPtr[i] = hostArray[i];
										udpSend(outBuffer, 3 + sizeof(struct hostData) * numHosts,
											leader);
									}
								}
								else
								{
									outBuffer[0] = ELECT_LEADER_RES;
									outBuffer[1] = 0;
									write2(&outBuffer[2], numHosts);
									hostDataPtr = (struct hostData *)&outBuffer[4];
									for (i = 0; i < numHosts; i++)
										hostDataPtr[i] = hostArray[i];
									udpSend(outBuffer, 4 + sizeof(struct hostData) * numHosts,
										electionParent);
								}
							}
						}
						break;
					case CONGRATS_CMD:
						if (state == ELECT2_STATE)
						{ //I am the leader
							leader = myHostData.ipAddr;
							dhtLog("I am the leader!\n");
							tmpUShort = read2(&inBuffer[1]);
							hostDataPtr = (struct hostData *)&inBuffer[3];
							for (i = 0; i < tmpUShort; i++)
								addHost(hostDataPtr[i]);
							writeHostList();
							setState(LEAD_REBUILD1_STATE);
							outBuffer[0] = REBUILD_CMD;
							udpSendAll(outBuffer, 1);
						}
						break;
					case REBUILD_REQ:
						if (state == LEAD_NORMAL1_STATE || state == LEAD_NORMAL2_STATE)
						{
							setState(LEAD_REBUILD1_STATE);
							outBuffer[0] = REBUILD_CMD;
							udpSendAll(outBuffer, 1);
						}
						break;
					case REBUILD_CMD:
						leader = peerIp; //consider this a declaration of authority
						setState(REBUILD1_STATE);
						outBuffer[0] = JOIN_REQ;
						write4(&outBuffer[1], myHostData.maxKeyCapacity);
						udpSend(outBuffer, 5, leader);
						break;
					case FILL_DHT_CMD:
						if (state == REBUILD3_STATE && peerIp == leader)
						{
							setState(REBUILD4_STATE);
							if (fillStatus != 0)
								dhtLog("udpListen(): ERROR: fillTask already running\n");
							fillStatus = 1;
							if (pthread_create(&threadFillTask, NULL, fillTask, NULL) != 0)
								dhtLog("udpListen(): ERROR creating threadFillTask\n");
						}
						break;
					case FILL_DHT_RES:
						if (state == LEAD_REBUILD3_STATE)
						{
							checkReplied(peerIp);
							if (allReplied() && fillStatus == 2)
							{
								fillStatus = 0;
								setState(LEAD_REBUILD4_STATE);
								outBuffer[0] = RESUME_NORMAL_CMD;
								udpSendAll(outBuffer, 1);
							}
						}
						break;
					case RESUME_NORMAL_CMD:
						if (state == REBUILD5_STATE && peerIp == leader)
						{
							setState(NORMAL_STATE);
							outBuffer[0] = RESUME_NORMAL_RES;
							udpSend(outBuffer, 1, leader);
						}
						break;
					case RESUME_NORMAL_RES:
						if (state == LEAD_REBUILD4_STATE)
						{
							checkReplied(peerIp);
							if (allReplied())
							{
								setState(LEAD_NORMAL1_STATE);
							}
						}
						break;
				}
			}
		}
		if (state == REBUILD4_STATE)
		{
			switch (fillStatus)
			{
				case 0: dhtLog("udpListen(): ERROR: fillStatus=0 in REBUILD4_STATE\n");
					break;
				case 1: //do nothing
					break;
				case 2: //done filling the dht, notify leader
					fillStatus = 0;
					setState(REBUILD5_STATE);
					outBuffer[0] = FILL_DHT_RES;
					udpSend(outBuffer, 1, leader);
					break;
				case 3: //error encountered -> restart rebuild
					fillStatus = 0;
					setState(REBUILD0_STATE);
					outBuffer[0] = REBUILD_REQ;
					udpSend(outBuffer, 1, leader);
					break;
			}
		}
		if (state == LEAD_REBUILD3_STATE)
		{
			switch (fillStatus)
			{
				case 0: dhtLog("udpListen(): ERROR: fillStatus=0 in LEAD_REBUILD3_STATE\n");
					break;
				case 1: //do nothing
					break;
				case 2: //I'm done, now is everybody else also done?
					if (allReplied())
					{
						fillStatus = 0;
						setState(LEAD_REBUILD4_STATE);
						outBuffer[0] = RESUME_NORMAL_CMD;
						udpSendAll(outBuffer, 1);
					}
					break;
				case 3: //error encountered -> restart rebuild
					fillStatus = 0;
					setState(LEAD_REBUILD1_STATE);
					outBuffer[0] = REBUILD_CMD;
					udpSendAll(outBuffer, 1);
					break;
			}
		}
		if (timerSet)
		{
			gettimeofday(&now, NULL);
			if (timercmp(&now, &timer, >))
			{
				if (timeoutCntr < retry_vals[state])
				{
					timeoutCntr++;
					timeradd(&now, &timeout_vals[state], &timer);
					dhtLog("udpListen(): retry: %d\n", timeoutCntr);
					switch (state)
					{
						case INIT1_STATE:
							outBuffer[0] = WHO_IS_LEADER_CMD;
							udpSend(outBuffer, 1, seed);
							break;
						case INIT2_STATE:
							outBuffer[0] = JOIN_REQ;
							write4(&outBuffer[1], myHostData.maxKeyCapacity);
							udpSend(outBuffer, 5, leader);
							break;
						case ELECT1_STATE:
							outBuffer[0] = ELECT_LEADER_CMD;
							write4(&outBuffer[1], electionOriginator);
							udpSendAll(outBuffer, 5);
							break;
						case ELECT2_STATE:
							if (electionOriginator == myHostData.ipAddr)
							{ //retry notify leader
								outBuffer[0] = CONGRATS_CMD;
								write2(&outBuffer[1], numHosts);
								memcpy(&outBuffer[3], hostArray, sizeof(struct hostData)
									* numHosts);
								udpSend(outBuffer, 3 + sizeof(struct hostData) * numHosts,
									leader);
							}
							else
							{
								outBuffer[0] = ELECT_LEADER_RES;
								outBuffer[1] = 0;
								write2(&outBuffer[2], numHosts);
								memcpy(&outBuffer[4], hostArray, sizeof(struct hostData)
									* numHosts);
								udpSend(outBuffer, 4 + sizeof(struct hostData) * numHosts,
									electionParent);
							}
							break;
						case REBUILD0_STATE:
							outBuffer[0] = REBUILD_REQ;
							udpSend(outBuffer, 1, leader);
							break;
						case REBUILD1_STATE:
							outBuffer[0] = JOIN_REQ;
							write4(&outBuffer[1], myHostData.maxKeyCapacity);
							udpSend(outBuffer, 5, leader);
							break;
						case REBUILD5_STATE:
							outBuffer[0] = FILL_DHT_RES;
							udpSend(outBuffer, 1, leader);
							break;
						case LEAD_REBUILD1_STATE:
							outBuffer[0] = REBUILD_CMD;
							udpSendAll(outBuffer, 1);
							break;
						case LEAD_REBUILD2_STATE:
							outBuffer[0] = DHT_UPDATE_CMD;
							write2(&outBuffer[1], numHosts);
							write2(&outBuffer[3], numBlocks);
							memcpy(&outBuffer[5], hostArray, numHosts
								* sizeof(struct hostData));
							memcpy(&outBuffer[5+numHosts*sizeof(struct hostData)],
								blockOwnerArray, numBlocks*2);
							udpSendAll(outBuffer, 5 + sizeof(struct hostData) * numHosts
								+ 2 * numBlocks);
							break;
						case LEAD_REBUILD3_STATE:
							outBuffer[0] = FILL_DHT_CMD;
							udpSendAll(outBuffer, 1);
							break;
						case LEAD_REBUILD4_STATE:
							outBuffer[0] = RESUME_NORMAL_CMD;
							udpSendAll(outBuffer, 1);
							break;
						case EXIT1_STATE: //TODO...
							break;
						case NORMAL_STATE:
						case LEAD_NORMAL1_STATE:
						case LEAD_NORMAL2_STATE:
						case REBUILD2_STATE:
						case REBUILD3_STATE:
						case REBUILD4_STATE:
						case EXIT2_STATE: //we shouldn't get here
							break;
					}
				}
				else
				{
					dhtLog("udpListen(): timed out in state %s after %d retries\n",
						state_names[state], timeoutCntr);
					switch (state)
					{
						case INIT1_STATE:
							setState(EXIT2_STATE);
							break;
						case LEAD_NORMAL2_STATE:
							setState(LEAD_REBUILD1_STATE);
							outBuffer[0] = REBUILD_CMD;
							udpSendAll(outBuffer, 1);
							break;
						case ELECT1_STATE:
							dhtLog("removing unresponsive hosts, before:\n");
							writeHostList();
							removeUnresponsiveHosts();
							dhtLog("after\n");
							writeHostList();
							setState(ELECT2_STATE);
							if (electionOriginator == myHostData.ipAddr)
							{
								leader = hostArray[0].ipAddr;
								if (leader == myHostData.ipAddr)
								{ //I am the leader
									dhtLog("I am the leader!\n");
									setState(LEAD_REBUILD1_STATE);
									outBuffer[0] = REBUILD_CMD;
									udpSendAll(outBuffer, 1);
								}
								else
								{ //notify leader
									outBuffer[0] = CONGRATS_CMD;
									write2(&outBuffer[1], numHosts);
									memcpy(&outBuffer[3], hostArray, sizeof(struct hostData)
										* numHosts);
									udpSend(outBuffer, 3 + sizeof(struct hostData) * numHosts,
										leader);
								}
							}
							else
							{
								outBuffer[0] = ELECT_LEADER_RES;
								outBuffer[1] = 0;
								write2(&outBuffer[2], numHosts);
								memcpy(&outBuffer[4], hostArray, sizeof(struct hostData)
									* numHosts);
								udpSend(outBuffer, 4 + sizeof(struct hostData) * numHosts,
									electionParent);
							}
							break;
						case INIT2_STATE:
						case ELECT2_STATE:
						case REBUILD0_STATE:
						case REBUILD1_STATE:
						case REBUILD2_STATE:
						case REBUILD3_STATE:
						case REBUILD4_STATE:
						case REBUILD5_STATE:
						case LEAD_REBUILD1_STATE:
						case LEAD_REBUILD2_STATE:
						case LEAD_REBUILD3_STATE:
						case LEAD_REBUILD4_STATE:
							//start election
							electionOriginator = myHostData.ipAddr;
							setState(ELECT1_STATE);
							outBuffer[0] = ELECT_LEADER_CMD;
							write4(&outBuffer[1], myHostData.ipAddr); //originator = me
							udpSendAll(outBuffer, 5);
							break;
						case EXIT1_STATE:
							setState(EXIT2_STATE);
							break;
						case NORMAL_STATE:
						case LEAD_NORMAL1_STATE:
						case EXIT2_STATE: //we shouldn't get here
							break;
					}
				}
			}
		}
		if (state != oldState)
			pthread_cond_broadcast(&stateCond);
		pthread_mutex_unlock(&stateMutex);
	}
}

