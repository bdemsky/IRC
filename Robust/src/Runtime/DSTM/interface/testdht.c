#include <stdio.h>
#include "dht.h"

int main()
{
	int i;

	dhtInit();
	sleep(1);
	
	for(i = 0; i < 3; i++)
	{
		dhtInsert(i, 10-i);
		sleep(1);
		dhtRemove(i);
		sleep(1);
		dhtSearch(i);
		sleep(1);
	}
	return 0;
}
