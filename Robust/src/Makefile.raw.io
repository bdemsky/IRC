
USEBOOTLOADER=no

ifeq ($(USEBOOTLOADER),yes)
ATTRIBUTES      += LARGE_STATIC_DATA
endif

# We need to define the host OS to get access
# to the host specific OS defines!   - VS 
DEFS	+= -D$(shell uname -s) -D__raw__

TOPDIR=/home/jzhou/starsearch
include $(TOPDIR)/Makefile.include

RGCCFLAGS += -O2 
RGCCFLAGS += ${RAWRGCCFLAGS} 

USE_SLGCC=1

SIM-CYCLES = 10000

ATTRIBUTES += HWIC

TILES = 00 01 02 03 04 05 06 07 08 09 10 11 12 13 14 15

#TILE_PATTERN = 4x1

OBJECT_FILES_COMMON = multicoretask.o multicoreruntime.o Queue.o file.o math.o object.o \
					  GenericHashtable.o SimpleHash.o ObjectHash.o socket.o \
					  taskdefs.o methods.o mem.o raw_dataCache.o raw_interrupt.o

OBJECT_FILES_00 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_01 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_02 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_03 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_04 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_05 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_06 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_07 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_08 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_09 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_10 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_11 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_12 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_13 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_14 = $(OBJECT_FILES_COMMON)
OBJECT_FILES_15 = $(OBJECT_FILES_COMMON)

# this is for a multi-tile test
include $(COMMONDIR)/Makefile.all

ifneq ($(USEBOOTLOADER),yes)
# Load the host interface and host OS simulator into btl
BTL-ARGS += -host # -imem_size 65536
endif

BTL-ARGS += -host_stop_time

