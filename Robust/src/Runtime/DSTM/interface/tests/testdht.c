#include <stdio.h>
#include "dht.h"
#include "clookup.h"

#define NUM_ITEMS 100000

int main()
{
	unsigned int key;
	unsigned int val;
	unsigned int vals[NUM_ITEMS];
	int retval;
	int error;
	chashtable_t *localHash;

	dhtInit(0x80C3AF45, DHT_NO_KEY_LIMIT);

	localHash = chashCreate(HASH_SIZE, LOADFACTOR);
	srandom(time(0));

	for (key = 1; key < NUM_ITEMS; key++)
	{
		vals[key] = random();
	}

	sleep(5);

	printf("testing dhtInsert() and dhtSearch()\n");

	for (key = 0; key < NUM_ITEMS; key++)
	{
		dhtInsert(key, vals[key]);
	}

	error = 0;
	for (key = 1; key < NUM_ITEMS; key++)
	{
		retval = dhtSearch(key, &val);
		if (retval == 1)
		{
			printf("item not found: key = %d, expected val = %d\n", key, vals[key]);
			error = 1;
		}
		else if (retval == -1)
		{
			printf("internal error: key = %d, expected val = %d\n", key, vals[key]);
			error = 1;
		}
		else if (retval == 0)
		{
			if (vals[key] != val)
			{
				printf("unexpected value: key = %d, expected val = %d, val = %d\n", key, vals[key], val);
				error = 1;
			}
		}
	}
	if (!error)
		printf("test completed successfully\n");
	else
		printf("one or more errors occurred\n");

	printf("(this currently fails if key = 0 OR val = 0, due to underlying hash table)\n");
	printf("testing underlying hash table (clookup.h)\n");

	for (key = 1; key < NUM_ITEMS; key++)
	{
		chashInsert(localHash, key, (void *)vals[key]);
	}

	error = 0;
	for (key = 1; key < NUM_ITEMS; key++)
	{
		val = (unsigned int)chashSearch(localHash, key);
		if ((void *)val == NULL)
		{
			printf("item not found: key = %d, expected val = %d\n", key, vals[key]);
			error = 1;
		}
		else
		{
			if (vals[key] != val)
			{
				printf("unexpected value: key = %d, expected val = %d, val = %d\n", key, vals[key], val);
				error = 1;
			}
		}
		for (key = NUM_ITEMS; key < NUM_ITEMS + 20; key++)
		{
			val = (unsigned int)chashSearch(localHash, key);
			if ((void *)val != NULL)
			{
				printf("error: returned value for key that wasn't inserted: key = %d, val = %d\n", key, val);
				error = 1;
			}
		}
	}

	if (!error)
		printf("test completed successfully\n");
	else
		printf("one or more errors occurred\n");

	printf("testing dhtRemove(), removing half of the keys, and verifying that the other half is still there\n");
	
	for (key = 0; key < NUM_ITEMS / 2; key++)
	{
		dhtRemove(key);
	}
	error = 0;
	for (key = 0; key < NUM_ITEMS / 2; key++)
	{
		retval = dhtSearch(key, &val);
		if (retval == 0)
		{
			printf("error: found removed item: key = %d, val = %d\n", key, val);
			error = 1;
		}
		else if (retval == -1)
		{
			printf("internal error: key = %d, val = %d\n", key, val);
			error = 1;
		}
	}
	for (key = NUM_ITEMS / 2; key < NUM_ITEMS; key++)
	{
		retval = dhtSearch(key, &val);
		if (retval == 1)
		{
			printf("item not found: key = %d, expected val = %d\n", key, vals[key]);
			error = 1;
		}
		else if (retval == -1)
		{
			printf("internal error: key = %d, expected val = %d\n", key, vals[key]);
			error = 1;
		}
		else if (retval == 0)
		{
			if (vals[key] != val)
			{
				printf("unexpected value: key = %d, expected val = %d, val = %d\n", key, vals[key], val);
				error = 1;
			}
		}
	}

	if (!error)
		printf("test completed successfully\n");
	else
		printf("one or more errors occurred\n");

	sleep(5);

	dhtExit();

	return 0;
}

