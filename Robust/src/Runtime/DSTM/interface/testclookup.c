#include <stdio.h>
#include "clookup.h"

main() 
{
	int i;
	void *val;
	cachehashtable_t *ctable;

	if (( ctable = cachehashCreate(20, 0.50)) == NULL) {
		printf("cachehashCreate error\n");	//creates hashtable
	}

	for (i = 1; i <= 10; i++) {	// Checks the insert() and resize() 
		if (cachehashInsert(ctable, 10*i, &i) == 1) 
			printf("cachehashInsert error\n");
	}

	i = cachehashRemove(ctable, 10);//Delete first element in the  hashtable
	if(i == 1)
		printf("cachehashRemove error ");
	
	for (i = 1; i <= 10; i++) { // Check if it can search for all keys in hash table
		val = cachehashSearch(ctable, 10*i);
		if (val != &i) 
			printf("cachehashSearch error - val = %d\n", val);
		else
			printf("cachehashSearch key = %d val = %x\n",10*i, val);
	}

	i = cachehashRemove(ctable, 30);
	if(i == 1)
		printf("cachehashRemove error\n ");
	i = cachehashRemove(ctable, 40);
	if(i == 1)
		printf("cachehashRemove error\n ");
	i = cachehashRemove(ctable, 80);
	if(i == 1)
		printf("cachehashRemove error\n ");
	i = cachehashRemove(ctable, 100);
	if(i == 1)
		printf("cachehashRemove error\n ");
	i = cachehashRemove(ctable, 90);
	if(i == 1)
		printf("cachehashRemove error\n ");
	
	for (i = 1; i <= 10; i++) {	//Prints all left over elements inside hash after deletion and prints error if element not found in hash
		val = cachehashSearch(ctable, 10*i);
		if (val != &i) 
			printf("cachehashSearch error - val = %d\n", val);
		else
			printf("cachehashSearch key = %d val = %x\n",10*i, val);
	}

	printf("The total number of elements in table : %d\n", ctable->numelements);

}

