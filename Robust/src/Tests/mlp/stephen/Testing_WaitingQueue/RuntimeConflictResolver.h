#ifndef __3_RCR_H_
#define __3_RCR_H_

//NOTE these files are the fake locks so I can test without compiling entire compiler
void traverse______b1745___sesea71___(void * InVar);
void traverse______b2746___seseb72___(void * InVar);

void traverse1(void * inVar);
void traverse2(void * inVar);

int traverse(void * startingPtr, int traverserID);
void createMasterHashStructureArray();
void initializeStructsRCR();
void destroyRCR(); 
#endif
