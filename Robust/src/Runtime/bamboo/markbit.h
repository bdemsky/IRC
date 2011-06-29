#ifndef MARKBIT_H
#define MARKBIT_H
#include "bambooalign.h"

extern unsigned int markmappingarray[];
extern unsigned int bitmarkmappingarray[];
extern unsigned int revmarkmappingarray[];

#define CONVERTTABLEINDEXTOPTR(x) (((unsigned INTPTR)((x)<<(ALIGNMENTSHIFT+4)))+gcbaseva)
//Minimum alignment unit



#define OBJMASK 0x40000000UL  //set towhatever smallest object mark is
#define MARKMASK 0xc0000000UL  //set towhatever smallest object mark is

/* 
   The bitmap mark array uses 2 mark bits per alignment unit.

   The clever trick is that we encode the length of the object (in
   units of alignment units) using just these two bits.  The basic
   idea is to generate a variable length encoding of the length in
   which the length of the encoding is shorter than number of mark
   bits taken up by the object.

   To make this efficient, it is table driven for objects that are
   less than 16 alignment units in length.  For larger objects, we
   just use addition.
*/

/* Return length in units of ALIGNSIZE */

static inline unsigned int getMarkedLength(void *ptr) {
  unsigned INTPTR alignsize=ALIGNOBJSIZE((unsigned INTPTR)(ptr-gcbaseva));
  unsigned INTPTR hibits=alignsize>>4;
  unsigned INTPTR lobits=(alignsize&15)<<1;
  unsigned INTPTR val;
  if (lobits==0)
    val=gcmarktbl[hibits];
  else {
    unsigned INTPTR revlobits=32-lobits;
    val=(gcmarktbl[hibits]<<lobits)|(gcmarktbl[hibits+1]>>(revlobits));
  }
  unsigned int index=val>>26;
  if (index>48)
    return (val-0xc4000000)+16;
  else
    return markmappingarray[index];
}

/* Return non-zero value if the object is marked */

static inline unsigned int checkMark(void *ptr) {
  unsigned INTPTR alignsize=ALIGNOBJSIZE((unsigned INTPTR)(ptr-gcbaseva));
  unsigned INTPTR hibits=alignsize>>4;
  unsigned INTPTR lobits=(alignsize&15)<<1;

  return (gcmarktbl[hibits]<<lobits)&MARKMASK;
}

/* Set length in units of ALIGNSIZE */

static inline void setLength(void *ptr, unsigned int length) {
  unsigned INTPTR alignsize=ALIGNOBJSIZE((unsigned INTPTR)(ptr-gcbaseva));
  unsigned INTPTR hibits=alignsize>>4;
  unsigned INTPTR lobits=(alignsize&15)<<1;
  unsigned int ormask=(length>=16)?0xc4000000+(length-16):revmarkmappingarray[length];
  if (lobits==0) {
    gcmarktbl[hibits]|=ormask;
  } else {
    gcmarktbl[hibits]|=ormask>>lobits;
    unsigned INTPTR lowormask=ormask<<(32-lobits);
    if (lowormask)
      gcmarktbl[hibits+1]|=lowormask;
  }
}

/* Set length for premarked object */

static inline void setLengthMarked_I(void *ptr, unsigned int length) {
  unsigned INTPTR alignsize=ALIGNOBJSIZE((unsigned INTPTR)(ptr-gcbaseva));
  unsigned INTPTR hibits=alignsize>>4;
  unsigned INTPTR lobits=(alignsize&15)<<1;
  unsigned int ormask=(length>=16)?0xc4000000+(length-16):revmarkmappingarray[length];
  if (lobits==0) {
    gcmarktbl[hibits]=(gcmarktbl[hibits]^(OBJMASK))|ormask;
  } else {
    gcmarktbl[hibits]=(gcmarktbl[hibits]^(OBJMASK>>lobits))|(ormask>>lobits);
    unsigned INTPTR lowormask=ormask<<(32-lobits);
    if (lowormask)
      gcmarktbl[hibits+1]|=lowormask;
  }
}

static inline void setLengthMarked(void *ptr, unsigned int length) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  setLengthMarked_I(ptr, length);
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
}

/* Set length in units of ALIGNSIZE */

static inline void setMark_I(void *ptr) {
  unsigned INTPTR alignsize=ALIGNOBJSIZE((unsigned INTPTR)(ptr-gcbaseva));
  unsigned INTPTR hibits=alignsize>>4;
  unsigned INTPTR lobits=(alignsize&15)<<1;
  gcmarktbl[hibits]|=(OBJMASK>>lobits);
}

static inline void setMark(void *ptr) {
  BAMBOO_ENTER_RUNTIME_MODE_FROM_CLIENT();
  setMark_I(ptr);
  BAMBOO_ENTER_CLIENT_MODE_FROM_RUNTIME();
}

static inline void clearMark(void *ptr) {
  unsigned INTPTR alignsize=ALIGNOBJSIZE((unsigned INTPTR)(ptr-gcbaseva));
  unsigned INTPTR hibits=alignsize>>4;
  unsigned INTPTR lobits=(alignsize&15)<<1;

  if (lobits==0) {
    unsigned int hipart=gcmarktbl[hibits];
    unsigned int index=hipart>>26;
    unsigned int bits=(index>48)?32:bitmarkmappingarray[index];
    unsigned int bitstotoss=32-bits;
    gcmarktbl[hibits]^=((hipart>>(bitstotoss))<<(bitstotoss));
  } else {
    unsigned int orighi=gcmarktbl[hibits];
    unsigned int hipart=orighi<<lobits;
    unsigned INTPTR revlobits=32-lobits;
    unsigned int lowpart=gcmarktbl[hibits+1]>>revlobits;
    unsigned INTPTR val=hipart|lowpart;
    
    unsigned int index=val>>26;
    unsigned int bits=(index>48)?32:bitmarkmappingarray[index];

    unsigned int bitstotoss=32-bits;

    if ((bits+lobits)<32) {
      unsigned int bitstotossminuslobits=bitstotoss-lobits;      
      gcmarktbl[hibits]^=(hipart>>bitstotoss)<<bitstotossminuslobits;
    } else
      gcmarktbl[hibits]^=hipart>>lobits;
    unsigned int xormask=(lowpart>>bitstotoss)<<(bitstotoss+revlobits);
    if (xormask)
      gcmarktbl[hibits+1]^=xormask;
  }
}

#endif
