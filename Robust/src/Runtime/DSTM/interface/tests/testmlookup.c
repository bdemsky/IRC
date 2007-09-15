#include <stdio.h>
#include "mlookup.h"
extern mhashtable_t mlookup;

main() 
{
	int i;
	void *val;
	val = NULL;

	if (mhashCreate(10, 0.20) == 1) {
		printf("mhashCreate error\n");	//creates hashtable
	}
	for (i = 1; i <= 7; i++) {	// Checks the insert() and resize() 
		if (mhashInsert(10*i, &i) == 1) 
			printf("mhashInsert error\n");
	}
	
	i = mhashRemove(60);//Delete first element in the  hashtable
	if(i == 1)
		printf("mhashRemove error ");
	
	for (i = 1; i <=7; i++) { // Check if it can search for all oids in hash table
		val = mhashSearch(10*i);
		if (val != &i) 
			printf("mhashSearch error - val = %d\n", val);
		else
			printf("mhashSearch oid = %d val = %x\n",10*i, val);
	}

	i = mhashRemove(30);
	if(i == 1)
		printf("mhashRemove error ");
	
	for (i = 1; i <= 7; i++) {	//Prints all left over elements inside hash after deletion and prints error if element not found in hash
		val = mhashSearch(10*i);
		if (val != &i) 
			printf("mhashSearch error - val = %d\n", val);
		else
			printf("mhashSearch oid = %d val = %x\n",10*i, val);
	}

	printf("The total number of elements in table : %d\n", mlookup.numelements);

}
