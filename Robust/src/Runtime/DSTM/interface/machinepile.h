#ifndef _MACHINEPILE_H_
#define _MACHINEPILE_H_

#include "mcpileq.h"
#include <stdio.h>
#include <stdlib.h>

//add prefetch site as an argument for debugging
void insertPile(int, unsigned int, int, short, short *, prefetchpile_t **);

#endif
