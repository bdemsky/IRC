#ifndef BAMBOOALIGN_H
#define BAMBOOALIGN_H

#define ALIGNMENTSIZE 32
//Bytes to shift to get minimum alignment units                                 
#define ALIGNMENTSHIFT 5
#define NOTMARKED 0
#define BITSPERALIGNMENT 2
#define ALIGNOBJSIZE(x) (x)>>ALIGNMENTSHIFT
#define ALIGNSIZETOBYTES(x) (x)<<ALIGNMENTSHIFT
#define ALIGNTOTABLEINDEX(x) (x)>>(ALIGNMENTSHIFT+4)


#endif
