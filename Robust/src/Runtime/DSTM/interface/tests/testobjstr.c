#include "dstm.h"

#define NUMITEMS 1000000 //uses four object stores

int main(void)
{
	objstr_t *myObjStr = objstrCreate(1048510);
	int i;
	int *j[NUMITEMS];
	int data[NUMITEMS];
	int fail = 0;
	
	for (i = 0; i < NUMITEMS; i++)
	{
		j[i] = objstrAlloc(myObjStr, sizeof(int));
		*j[i] = data[i] = i;
	}
	for (i = 0; i < NUMITEMS; i++)
	{
		if (data[i] != *j[i])
			fail = 1;
	}

	if (fail)
		printf("test failed\n");
	else
		printf("test succeeded\n");
	
	objstrDelete(myObjStr);
	return 0;
}

