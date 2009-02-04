TOPDIR=/home/jzhou/starsearch
include $(TOPDIR)/Makefile.include

RGCCFLAGS += -O2 
RGCCFLAGS += -DRAW 

USE_SLGCC=1

SIM-CYCLES = 10000

ATTRIBUTES += HWIC

TILES = 00

OBJECT_FILES_00 = filterbank.o
#OBJECT_FILES = JGFSeriesBench.o

# this is for a multi-tile test
include $(COMMONDIR)/Makefile.all
#include $(COMMONDIR)/Makefile.single
