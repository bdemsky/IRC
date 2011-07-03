#include "multicoreruntime.h"
#include "multicoremem.h"
#include "multicoregarbage.h"
#include "markbit.h"

unsigned int markmappingarray[]={0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 
				 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 
				 2, 2, 2, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 
				 15};


unsigned int bitmarkmappingarray[]={2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 
				    2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 
				    4, 4, 4, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 
				    30};

unsigned int revmarkmappingarray[]={0x0, 0x40000000, 0x80000000, 0x90000000, 0x94000000, 0x98000000, 0x9c000000, 0xa0000000, 0xa4000000, 0xa8000000, 0xac000000, 0xb0000000, 0xb4000000, 0xb8000000, 0xbc000000, 0xc0000000};


/*int main(int argv, char **argc) {
  void *ptr=1024;
  unsigned int i;
  gcmarktbl[0]=0xf000ffff;
  gcmarktbl[1]=0xffffffff;
  for(i=0;i<36;i++) {
    setLength(ptr, i);
    printf("%d\n",getMarkedLength(ptr));
    printf("%x %x %x\n", gcmarktbl[0], gcmarktbl[1], gcmarktbl[2]);
    clearMark(ptr);
    printf("%x %x %x\n", gcmarktbl[0], gcmarktbl[1], gcmarktbl[2]);
  }
  }*/

