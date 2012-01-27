#!/bin/bash

bnum="0"

BDIR[$bnum]=crypt
bnum=$[$bnum+1]

BDIR[$bnum]=kmeans
bnum=$[$bnum+1]
#
#BDIR[$bnum]=labyrinth
#bnum=$[$bnum+1]
#
#BDIR[$bnum]=moldyn
#bnum=$[$bnum+1]
#
#BDIR[$bnum]=monte
#bnum=$[$bnum+1]
#
#BDIR[$bnum]=power
#bnum=$[$bnum+1]
#
#BDIR[$bnum]=raytracing
#bnum=$[$bnum+1]
#
#BDIR[$bnum]=tracking
#bnum=$[$bnum+1]



CSV=count-elements-exp.csv

CUR=$PWD
#echo 'Count Graph Elements Experiment' > $CSV


i="0"
while [ $i -lt $bnum ]; do
  cd $CUR/${BDIR[$i]}

  pwd

  cd $CUR
  i=$[$i+1]
done
