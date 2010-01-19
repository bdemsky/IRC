# !/bin/sh
i=1;
fileName=$1
k=8;
while [ $i -le 8 ]; do
  echo "killing dc-$i ${fileName}"
  ssh dc-${i} pkill -u jihoonl -f ${fileName}
  i=`expr $i + 1`
done
