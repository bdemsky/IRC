#ifndef _DHT_H
#define _DHT_H

#define INIT_NUM_BLOCKS 16

//messages
#define DHT_INSERT 1
#define DHT_REMOVE 2
#define DHT_SEARCH 3
#define DHT_ACK 4
#define DHT_JOIN 5
#define DHT_LEAVE 6
#define DHT_REBUILD 7
//etc...

struct hostData {
	unsigned int ipAddr;
	unsigned int maxKeyCapacity;
	struct hostData *next;
};

struct dhtInsertMsg {
	unsigned char msgType;
	unsigned int unused:12;
	unsigned int key;
	unsigned int val;
};

struct dhtRemoveMsg {
	unsigned char msgType;
	unsigned int unused:12;
	unsigned int key;
};

struct dhtSearchMsg {
	unsigned char msgType;
	unsigned int unused:12;
	unsigned int key;
};

struct dhtJoinMsg {
	unsigned char msgType;
	unsigned int unused:12;
	struct hostData newHost;
};

//called by host which joins (or starts) the system
void dhtInit();
//exit system, cleanup
void dhtExit();

//called by whoever performs the creation, move, deletion
int dhtInsert(unsigned int key, unsigned int val);
int dhtRemove(unsigned int key);
int dhtSearch(unsigned int key);

#endif

