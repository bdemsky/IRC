#ifndef _ip_h_
#define _ip_h_

typedef struct ip {
	short a;
	short b;
	short c;
	short d;
}ip_t;

unsigned int iptoMid(char *);
void midtoIP(unsigned int, char *);

#endif
