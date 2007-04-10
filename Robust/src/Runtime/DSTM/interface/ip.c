#include <stdio.h>
#include <stdlib.h>
#include "ip.h"

unsigned int iptoMid(char *addr) {
	ip_t i;
	unsigned int mid;

	sscanf(addr, "%d.%d.%d.%d", &i.a, &i.b, &i.c, &i.d);
	mid = (i.a << 24) | (i.b << 16) | (i.c << 8) | i.d;
	return mid;
}

void midtoIP(unsigned int mid, char *ptr) {
	ip_t i;

	i.a = (mid & 0xff000000) >> 24;
	i.b = (mid & 0x00ff0000) >> 16;
	i.c = (mid & 0x0000ff00) >> 8;
	i.d = mid & 0x000000ff;
	sprintf(ptr, "%d.%d.%d.%d", i.a, i.b, i.c, i.d);
	return;
}

/*
main() {
	unsigned int mid;
	ip_t i;
	char ip[16];

	memset(ip, 0, 16);
	mid = iptoMid("192.10.0.1");
	printf("mid = %x\n", mid);
	midtoIP(mid, ip);
	ip[15] = '\0';
	printf("%s\n",ip);
}
*/
