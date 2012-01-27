#!/bin/bash

bnum="0"

BDIR[$bnum]=crypt
bnum=$[$bnum+1]

BDIR[$bnum]=kmeans
bnum=$[$bnum+1]

BDIR[$bnum]=labyrinth
bnum=$[$bnum+1]

BDIR[$bnum]=moldyn
bnum=$[$bnum+1]

BDIR[$bnum]=monte
bnum=$[$bnum+1]

BDIR[$bnum]=power
bnum=$[$bnum+1]

BDIR[$bnum]=raytracer
bnum=$[$bnum+1]

BDIR[$bnum]=tracking
bnum=$[$bnum+1]


mnum="0"

MODE[$mnum]=""
mnum=$[$mnum+1]

MODE[$mnum]="-disjoint-disable-strong-update"
mnum=$[$mnum+1]

MODE[$mnum]="-disjoint-disable-global-sweep"
mnum=$[$mnum+1]

MODE[$mnum]="-disjoint-disable-strong-update -disjoint-disable-global-sweep"
mnum=$[$mnum+1]

MODE[$mnum]="-do-definite-reach-analysis"
mnum=$[$mnum+1]

MODE[$mnum]="-do-definite-reach-analysis -disjoint-disable-strong-update"
mnum=$[$mnum+1]

MODE[$mnum]="-do-definite-reach-analysis -disjoint-disable-global-sweep"
mnum=$[$mnum+1]

MODE[$mnum]="-do-definite-reach-analysis -disjoint-disable-strong-update -disjoint-disable-global-sweep"
mnum=$[$mnum+1]


CURDIR=`pwd`

CSV=$CURDIR/count-elements-exp.csv
rm -f $CSV
touch $CSV

m="0"
while [ $m -lt $mnum ]; do

  b="0"
  while [ $b -lt $bnum ]; do

    cd $CURDIR/${BDIR[$b]}
  
    make ooo-debug "DISJOINTDEBUGEXTRAS=${MODE[$m]}"
    cat cge.txt >> $CSV
    make clean

    b=$[$b+1]

    if [ $b -lt $[$bnum-1] ]; then
      cd $CURDIR;
      cat comma.txt >> $CSV
    fi
  done

  echo "" >> $CSV

  m=$[$m+1]
done
