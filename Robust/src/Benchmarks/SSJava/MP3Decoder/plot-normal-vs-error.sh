#!/bin/bash

D=nve-diff.tmp
diff normal.txt error.txt > $D

X=nve-diff-ranges.tmp
echo '0 1' >  $X
echo '0 0' >> $X

#diff normal.txt error.txt | \
#sed \
#-e '/^[^0-9]/ d' \
#-e 's/\(.*\),\(.*\)c.*/\1 0\n\1 1\n\2 1\n\2 0/' \
#-e 's/\(.*\)c.*/\1 0\n\1 1\n\1 1\n\1 0/' \
#>> $X

sed \
-e '/^[^0-9]/ d' \
-e 's/\(.*\),\(.*\)c.*/\1 0\n\1 1\n\2 1\n\2 0/' \
-e 's/\(.*\)c.*/\1 0\n\1 1\n\1 1\n\1 0/' \
$D >> $X

if [[ -s $D ]] 
then
  echo 'Normal and Error files differ.'
else
  echo 'NO DIFF!'
fi

gnuplot -persist nve.cmds

rm -f $D $X
