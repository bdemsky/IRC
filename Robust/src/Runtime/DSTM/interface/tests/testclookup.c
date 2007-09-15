#include <stdio.h>
#include "clookup.h"

main() 
{
	int i;
	void *val;
	chashtable_t *ctable;

	if (( ctable = chashCreate(1000, 0.40)) == NULL) {
		printf("chashCreate error\n");	//creates hashtable
	}

	for (i = 1; i <= 2000; i++) {	// Checks the insert() and resize() 
		if (chashInsert(ctable, 10*i, &i) == 1) 
			printf("chashInsert error\n");
	}

	i = chashRemove(ctable, 10);//Delete first element in the  hashtable
	if(i == 1)
		printf("chashRemove error ");
	
	for (i = 1; i <= 2000; i++) { // Check if it can search for all keys in hash table
		val = chashSearch(ctable, 10*i);
		if (val != &i) 
			printf("chashSearch error - val = %d\n", val);
		else
			printf("chashSearch key = %d val = %x\n",10*i, val);
	}

	i = chashRemove(ctable, 30);
	if(i == 1)
		printf("chashRemove error\n ");
	i = chashRemove(ctable, 40);
	if(i == 1)
		printf("chashRemove error\n ");
	i = chashRemove(ctable, 80);
	if(i == 1)
		printf("chashRemove error\n ");
	i = chashRemove(ctable, 100);
	if(i == 1)
		printf("chashRemove error\n ");
	i = chashRemove(ctable, 90);
	if(i == 1)
		printf("chashRemove error\n ");
	
	for (i = 1; i <= 2000; i++) {	//Prints all left over elements inside hash after deletion and prints error if element not found in hash
		val = chashSearch(ctable, 10*i);
		if (val != &i) 
			printf("chashSearch error - val = %d\n", val);
		else
			printf("chashSearch key = %d val = %x\n",10*i, val);
	}

	printf("The total number of elements in table : %d\n", ctable->numelements);
	
	chashDelete(ctable);
}
