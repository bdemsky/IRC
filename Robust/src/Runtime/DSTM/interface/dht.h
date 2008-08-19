#ifndef _DHT_H
#define _DHT_H

#include <stdio.h>

/*******************************************************************************
*                             Local Structs
*******************************************************************************/

#define DHT_NO_KEY_LIMIT 0xFFFFFFFF

/*******************************************************************************
*                       Interface Function Prototypes
*******************************************************************************/

//called by host which joins (or starts) the system
void dhtInit(unsigned int seedIp, unsigned int maxKeyCapaciy);
//exit system, cleanup
void dhtExit();

//called by whoever performs the creation, move, deletion

//returns 0 if successful, -1 if an error occurred
int dhtInsert(unsigned int key, unsigned int val);
//simultaneously inserts the key-val pairs in the given arrays
int dhtInsertMult(unsigned int numKeys, unsigned int *keys,     unsigned int *vals);
//returns 0 if successful, -1 if an error occurred
int dhtRemove(unsigned int key);
//simultaneously delete the keys in the given array
int dhtRemoveMult(unsigned int numKeys, unsigned int *keys);
//returns 0 if successful and copies val into *val,
// 1 if key not found, -1 if an error occurred
int dhtSearch(unsigned int key, unsigned int *val);
//simultaneously search for the vals that correspond to the given keys.
// result is placed in vals[]
int dhtSearchMult(unsigned int numKeys, unsigned int *keys, unsigned int *vals);
#endif

