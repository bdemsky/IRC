#!/bin/sh

function run {
  echo ------------Running $1 --------------------
  for file in `ls STATS*`
    do
      num=`echo $file | tr -d ".bin" | tr -d "STATSSingleObjectMod"`
      echo $num
      /usr/bin/time -f "%e" -o /tmp/time ./$file $1 -o > /tmp/out
      t=`cat /tmp/time`
      nTcommit=`grep TransCommit /tmp/out | awk '{print $3}'`
      nTabort=`grep TransAbort /tmp/out | awk '{print $3}'`
      nSftabort=`grep 'nSoftAbort ' /tmp/out | awk '{print $3}'`
      p=`echo "$nTabort $nSftabort $nTcommit" | awk '{print (($1+$2)/($3))*100}'`
      echo "$num $t $p $file" >> runlog/$2
    done
}

echo "--------- Clean old files ----------"
#rm runlog/*.txt
#make base
#make stmlock
ARGS1="-t 8 -size 4 -l 100000 -l1 20000 -l2 40000"
ARGS2="-t 8 -size 4 -l 100000 -l1 10000 -l2 20000"
ARGS3="-t 8 -size 4 -l 100000 -l1 5000 -l2 10000"
ARGS4="-t 8 -size 4 -l 100000 -l1 2500 -l2 5000"
ARGS5="-t 8 -size 4 -l 100000 -l1 40000 -l2 20000"
ARGS6="-t 8 -size 4 -l 100000 -l1 10000 -l2 5000"
ARGS7="-t 8 -size 4 -l 100000 -l1 20000 -l2 10000"
ARGS8="-t 1 -size 1 -l 100000 -l1 10000 -l2 10000"
ARGS38="-t 2 -size 1 -l 100000 -l1 10000 -l2 10000"
ARGS48="-t 4 -size 1 -l 100000 -l1 10000 -l2 10000"
ARGS58="-t 8 -size 1 -l 100000 -l1 10000 -l2 10000"
ARGS68="-t 10 -size 1 -l 100000 -l1 10000 -l2 10000"
ARGS78="-t 12 -size 1 -l 100000 -l1 10000 -l2 10000"
ARGS88="-t 16 -size 1 -l 100000 -l1 10000 -l2 10000"
ARGS9="-t 8 -size 4 -l 100000 -l1 0 -l2 0"
ARGS10="-t 8 -size 1 -l 100000 -l1 40000 -l2 40000 -p 90"
ARGS11="-t 8 -size 1 -l 100000 -l1 10000 -l2 10000 -p 90"
ARGS12="-t 8 -size 1 -l 100000 -l1 40000 -l2 10000 -p 90"
ARGS13="-t 8 -size 4 -l 100000 -l1 10000 -l2 40000"
ARGS14="-t 8 -size 4 -l 100000 -l1 10000 -l2 60000"
ARGS15="-t 8 -size 4 -l 100000 -l1 80000 -l2 10000"
ARGS16="-t 8 -size 4 -l 100000 -l1 70000 -l2 10000"
ARGS17="-t 8 -size 4 -l 100000 -l1 70000 -l2 70000"
ARGS18="-t 1 -size 1 -l 100000 -l1 1 -l2 1"
ARGS19="-t 2 -size 1 -l 100000 -l1 1 -l2 1"
ARGS20="-t 4 -size 1 -l 100000 -l1 1 -l2 1"
ARGS21="-t 8 -size 1 -l 100000 -l1 1 -l2 1"
ARGS22="-t 12 -size 1 -l 100000 -l1 1 -l2 1"
ARGS23="-t 16 -size 1 -l 100000 -l1 1 -l2 1"
ARGS24="-t 1 -size 1 -l 1 -l1 1 -l2 1"
ARGS25="-t 1 -size 1 -l 1 -l1 0 -l2 0"
ARGS26="-t 1 -size 1 -l 100000 -l1 0 -l2 0"
ARGS27="-t 2 -size 1 -l 100000 -l1 0 -l2 0"
ARGS28="-t 4 -size 1 -l 100000 -l1 0 -l2 0"
ARGS29="-t 8 -size 1 -l 100000 -l1 0 -l2 0"

#run "$ARGS1" l1_20000_l2_40000.txt
#run "$ARGS2" l1_10000_l2_20000.txt
#run "$ARGS3" l1_5000_l2_10000.txt
#run "$ARGS4" l1_2500_l2_5000.txt
#run "$ARGS5" l1_40000_l2_20000.txt
#run "$ARGS6" l1_10000_l2_5000.txt
#run "$ARGS7" l1_20000_l2_10000.txt
#run "$ARGS8" lockunlock_l_100000_l1_10000_l2_10000.txt
#run "$ARGS38" lockunlock_l_100000_l1_10000_l2_10000.txt
#run "$ARGS48" lockunlock_l_100000_l1_10000_l2_10000.txt
#run "$ARGS58" lockunlock_l_100000_l1_10000_l2_10000.txt
#run "$ARGS68" lockunlock_l_100000_l1_10000_l2_10000.txt
#run "$ARGS78" lockunlock_l_100000_l1_10000_l2_10000.txt
#run "$ARGS88" lockunlock_l_100000_l1_10000_l2_10000.txt
#run "$ARGS9" l1_0_l2_0.txt
run "$ARGS10" l1_40000_l2_40000.txt
run "$ARGS11" l1_10000_l2_10000.txt
run "$ARGS12" l1_40000_l2_10000.txt
#run "$ARGS13" l1_10000_l2_40000.txt
#run "$ARGS14" l1_10000_l2_60000.txt
#run "$ARGS15" l1_10000_l2_80000.txt
#run "$ARGS16" l1_10000_l2_70000.txt
#run "$ARGS17" l1_70000_l2_70000.txt
#run "$ARGS18" t_1_l1_1_l2_1.txt
#run "$ARGS19" t_2_l1_1_l2_1.txt
#run "$ARGS20" t_4_l1_1_l2_1.txt
#run "$ARGS21" t_8_l1_1_l2_1.txt
#run "$ARGS22" t_12_l1_1_l2_1.txt
#run "$ARGS23" t_16_l1_1_l2_1.txt
#run "$ARGS24" t_1_l1_1_l2_1.txt
#run "$ARGS25" t_1_l1_0_l2_0.txt
#run "$ARGS26" l_100000_l1_0_l2_0.txt
#run "$ARGS27" l_100000_l1_0_l2_0.txt
#run "$ARGS28" l_100000_l1_0_l2_0.txt
#run "$ARGS29" l_100000_l1_0_l2_0.txt


## --------- Cut the first line from the .txt files generated above and plot them ---------
#for file in `ls runlog/*.plt`
#do
#basetime=`cat $file | grep "NLkBas" | cut -f2 -d" "`
#  gnuplot $file
#done

#high contention 4 threads
#ARGS4="-t 4 -size 5 -l 10000 -l1 100 -l2 50000"
#ARGS4="-t 4 -size 4 -l 10000 -l1 10000 -l2 10000" #44.1% abort
#high contention 8 threads
#ARGS8="-t 8 -size 4 -l 100000 -l1 0 -l2 0"
#ARGS8="-t 8 -size 4 -l 100000 -l1 20000 -l2 40000"
#-t 8 -size 4 -l 100000 -l1 10000 -l2 20000"
#-t 8 -size 4 -l 100000 -l1 5000 -l2 10000"
#-t 8 -size 4 -l 100000 -l1 2500 -l2 5000"
#-t 8 -size 4 -l 100000 -l1 40000 -l2 20000"
#-t 8 -size 4 -l 100000 -l1 10000 -l2 5000"
#-t 8 -size 4 -l 100000 -l1 20000 -l2 10000"
#-t 8 -size 4 -l 100000 -l1 10000 -l2 10000"
#ARGS8="-t 8 -size 4 -l 10000 -l1 0 -l2 0"
#ARGS8="-t 8 -size 6 -l 10000 -l1 5000 -l2 5000" #55.79% abort
#for file in `ls STATS*`
#do
#  num=`echo $file | tr -d ".bin" | tr -d "STATSSingleObjectMod"`
#  echo $num
##  /usr/bin/time -f "%e" -o /tmp/time ./$file $ARGS4 -o > /tmp/out
#  /usr/bin/time -f "%e" -o /tmp/time ./$file $1 -o > /tmp/out
#  t=`cat /tmp/time`
#  nTcommit=`grep TransCommit /tmp/out | awk '{print $3}'`
#  nTabort=`grep TransAbort /tmp/out | awk '{print $3}'`
#  nSftabort=`grep 'nSoftAbort ' /tmp/out | awk '{print $3}'`
#  p=`echo "$nTabort $nSftabort $nTcommit" | awk '{print (($1+$2)/($1+$2+$3))*100}'`
#  echo "$num $t $p $file" >> data.file
#done
