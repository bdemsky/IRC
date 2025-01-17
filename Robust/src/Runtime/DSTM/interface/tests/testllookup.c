#include <stdio.h>
#include "llookup.h"
extern lhashtable_t llookup;

main() 
{
	int i, mid;

	if (lhashCreate(10, 0.20) == 1) {
		printf("lhashCreate error\n");	//creates hashtable
	}
	for (i = 1; i <= 7; i++) {	// Checks the insert() and resize() 
		if (lhashInsert(10*i, i) == 1) 
			printf("lhashInsert error\n");
	}

	i = lhashRemove(10);//Delete first element in the  hashtable
	if(i == 1)
		printf("lhashRemove error ");
	
	for (i = 1; i <=7; i++) { // Check if it can search for all oids in hash table
		mid = lhashSearch(10*i);
		if (mid != i) 
			printf("lhashSearch error - mid = %d\n", mid);
		else
			printf("lhashSearch oid = %d mid = %d\n",10*i, mid);
	}

	i = lhashRemove(60);
	if(i == 1)
		printf("lhashRemove error ");
	
	for (i = 1; i <= 7; i++) {	//Prints all left over elements inside hash after deletion and prints error if element not found in hash
		mid = lhashSearch(10*i);
		if (mid != i) 
			printf("lhashSearch error - mid = %d\n", mid);
		else
			printf("lhashSearch oid = %d mid = %d\n",10*i, mid);
	}

	printf(" The total number of elements in table : %d\n", llookup.numelements);

}
