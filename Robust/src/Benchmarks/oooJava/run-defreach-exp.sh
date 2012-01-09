#!/bin/bash

CUR=`pwd`
echo 'Definite Reachability Experiment' > defreachexp.txt

for i in *
do
if [ -d "$i" ] ; then
echo ENTERING $i 
echo '' >> defreachexp.txt
echo "$i" >> defreachexp.txt
cd $i

echo 'for NORMAL' >> ../defreachexp.txt
for c in 1 2 3 4 5 6 7 8 9 10
do
make clean; make disjoint >> TEMP
done
grep "Fixed point algorithm" TEMP >> ../defreachexp.txt

echo 'for DEFREACH' >> ../defreachexp.txt
for c in 1 2 3 4 5 6 7 8 9 10
do
make clean; make disjoint-defreach >> TEMP
done
grep "Fixed point algorithm" TEMP >> ../defreachexp.txt

make clean
rm -f TEMP
cd $CUR
fi
done
