#ifndef _DHT_H
#define _DHT_H

//#define SIMPLE_DHT

#define DHT_NO_KEY_LIMIT 0xFFFFFFFF

//called by host which joins (or starts) the system
void dhtInit(unsigned int maxKeyCapaciy);
//exit system, cleanup
void dhtExit();

//called by whoever performs the creation, move, deletion

//returns 0 if successful, -1 if an error occurred
int dhtInsert(unsigned int key, unsigned int val);
//returns 0 if successful, -1 if an error occurred
int dhtRemove(unsigned int key);
//returns 0 if successful and copies val into *val,
// 1 if key not found, -1 if an error occurred
int dhtSearch(unsigned int key, unsigned int *val);

#endif

