TOPDIR=/home/jzhou/starsearch
include $(TOPDIR)/Makefile.include

RGCCFLAGS += -O2 
RGCCFLAGS += -DRAW 

SIM-CYCLES = 10000

ATTRIBUTES += HWIC

TILES = 00

OBJECT_FILES_00 = JGFSeriesBench.o
#OBJECT_FILES = JGFSeriesBench.o

# this is for a multi-tile test
include $(COMMONDIR)/Makefile.all
#include $(COMMONDIR)/Makefile.single
