#include "runtime.h"
#include "multicoreruntime.h"
#include "runtime_arch.h"
#include "multicore_arch.h"
#include "multicoremem_helper.h"


void buildCore2Test() {
  for(int i=0;i<NUMCORES4GC;i++) {
    int xcoord=BAMBOO_COORDS_X(i);
    int ycoord=BAMBOO_COORDS_Y(i);
    int index=0;
    for(int x=xcoord-1;x<=(xcoord+1);x++) {
      for(int y=ycoord-1;y<=(ycoord+1);y++) {
	if ((x<0||x>7)||(y<0||y>7)) {
	  //bad coordinate
	  core2test[i][index]=-1;
	} else {
	  int corenum=BAMBOO_CORE(x,y);
	  if (corenum<0||corenum>=NUMCORES4GC) {
	    core2test[i][index]=-1;
	  } else {
	    core2test[i][index]=corenum;
	  }
	}
	index++;
      }
    }
  }
}
