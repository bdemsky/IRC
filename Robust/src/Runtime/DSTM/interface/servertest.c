#include <pthread.h>
#include "dstmserver.h"

int main()
{
	pthread_t thread_listen;
	pthread_create(&thread_listen, NULL, dstmListen, NULL);	
	pthread_join(thread_listen, NULL);
	return 0;
}

