#!/bin/sh 

#set -x
MACHINELIST='dc-1.calit2.uci.edu dc-2.calit2.uci.edu dc-3.calit2.uci.edu dc-4.calit2.uci.edu dc-5.calit2.uci.edu dc-6.calit2.uci.edu dc-7.calit2.uci.edu dc-8.calit2.uci.edu'
benchmarks='array chase tree 65050mmver 15005fft2d moldynverB sorverD 1000010002dconv spamfilter2500 rainforest'


LOGDIR=~/research/Robust/src/Benchmarks/Prefetch/runlog
LOGDIR1=~/research/Robust/src/Benchmarks/Prefetch/logres
TOPDIR=`pwd`

function run {
  i=0;
  DIR=`pwd`
  HOSTNAME=`hostname`
  while [ $i -lt $1 ]; do
    echo "$DIR" > ~/.tmpdir
    echo "bin=$3" > ~/.tmpvars
    ct=0
    MACHINES=''
    for j in $MACHINELIST; do
      if [ $ct -lt $2 ]; then
        if [ "$j" != "$HOSTNAME" ]; then
          MACHINES="$MACHINES $j"
        fi
      fi
      let ct=$ct+1
    done

    rm dstm.conf
    DSTMDIR=${HOME}/research/Robust/src/Benchmarks/Prefetch/config
    if [ $2 -eq 2 ]; then 
      arg=$ARGS2
      ln -s ${DSTMDIR}/dstm_2.conf dstm.conf
    fi
    if [ $2 -eq 3 ]; then 
      arg=$ARGS3
      ln -s ${DSTMDIR}/dstm_3.conf dstm.conf
    fi
    if [ $2 -eq 4 ]; then 
      arg=$ARGS4
      ln -s ${DSTMDIR}/dstm_4.conf dstm.conf
    fi
    if [ $2 -eq 5 ]; then 
      arg=$ARGS5
      ln -s ${DSTMDIR}/dstm_5.conf dstm.conf
    fi
    if [ $2 -eq 6 ]; then 
      arg=$ARGS6
      ln -s ${DSTMDIR}/dstm_6.conf dstm.conf
    fi
    if [ $2 -eq 7 ]; then 
      arg=$ARGS7
      ln -s ${DSTMDIR}/dstm_7.conf dstm.conf
    fi
    if [ $2 -eq 8 ]; then 
      arg=$ARGS8
      ln -s ${DSTMDIR}/dstm_8.conf dstm.conf
    fi
    chmod +x ~/.tmpvars
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'cd `cat ~/.tmpdir`; source ~/.tmpvars; ./$bin' &
      echo ""
    done
    sleep 2
    perl -x${TOPDIR} ${TOPDIR}/switch/fetch_stat.pl clear_stats settings=switch/clearsettings.txt
    /usr/bin/time -f "%e" ./$3 master $arg 2> ${LOGDIR}/tmp
    perl -x${TOPDIR} ${TOPDIR}/switch/fetch_stat.pl settings=switch/settings.txt
    cat ${LOGDIR}/tmp >> ${LOGDIR}/${3}_${2}Thrd_${EXTENSION}.txt
    if [ $i -eq 0 ];then echo "<h3> Benchmark=${3} Thread=${2} Extension=${EXTENSION}</h3><br>" > ${LOGDIR}/${3}_${EXTENSION}_${2}Thrd_a.html  ;fi
    cat ${LOGDIR}/tmp >> ${LOGDIR}/${3}_${EXTENSION}_${2}Thrd_a.html
    echo "<a href=\"${3}_${2}Thrd_${EXTENSION}_${i}.html\">Network Stats</a><br>" >> ${LOGDIR}/${3}_${EXTENSION}_${2}Thrd_a.html
    mv ${TOPDIR}/html/dell.html ${LOGDIR}/${3}_${2}Thrd_${EXTENSION}_${i}.html
    echo "Terminating ... "
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'source ~/.tmpvars; killall $bin'
    done
    sleep 2
    i=`expr $i + 1`
  done
}

function runallstats {
  i=0;
  DIR=`pwd`
  HOSTNAME=`hostname`
  while [ $i -lt $1 ]; do
    echo "$DIR" > ~/.tmpdir
    echo "bin=$3" > ~/.tmpvars
    ct=0
    MACHINES=''
    for j in $MACHINELIST; do
      if [ $ct -lt $2 ]; then
        if [ "$j" != "$HOSTNAME" ]; then
          MACHINES="$MACHINES $j"
        fi
      fi
      let ct=$ct+1
    done

    rm dstm.conf
    DSTMDIR=${HOME}/research/Robust/src/Benchmarks/Prefetch/config
    if [ $2 -eq 2 ]; then 
      arg=$ARGS2
      ln -s ${DSTMDIR}/dstm_2.conf dstm.conf
    fi
    if [ $2 -eq 3 ]; then 
      arg=$ARGS3
      ln -s ${DSTMDIR}/dstm_3.conf dstm.conf
    fi
    if [ $2 -eq 4 ]; then 
      arg=$ARGS4
      ln -s ${DSTMDIR}/dstm_4.conf dstm.conf
    fi
    if [ $2 -eq 5 ]; then 
      arg=$ARGS5
      ln -s ${DSTMDIR}/dstm_5.conf dstm.conf
    fi
    if [ $2 -eq 6 ]; then 
      arg=$ARGS6
      ln -s ${DSTMDIR}/dstm_6.conf dstm.conf
    fi
    if [ $2 -eq 7 ]; then 
      arg=$ARGS7
      ln -s ${DSTMDIR}/dstm_7.conf dstm.conf
    fi
    if [ $2 -eq 8 ]; then 
      arg=$ARGS8
      ln -s ${DSTMDIR}/dstm_8.conf dstm.conf
    fi
    envarg=`echo $arg | sed 's/ /_/g'`
    FILENAME="${LOGDIR1}/${3}_${envarg}_thd_${2}_"
    echo "$FILENAME" > ~/.tmpenvs
    chmod +x ~/.tmpenvs
    chmod +x ~/.tmpvars
    echo "args=$arg thds=${2}Thd" > ~/.tmpparams
    for machine in `echo $MACHINES`
    do
      ssh ${machine} 'cd `cat ~/.tmpdir`; source ~/.tmpvars; /usr/bin/time -f "%e" ./$bin 2>> ./clienttime_`hostname | cut -f1 -d"."`.txt' &
      echo ""
    done
    sleep 2
    perl -x${TOPDIR} ${TOPDIR}/switch/fetch_stat.pl clear_stats settings=switch/clearsettings.txt
    /usr/bin/time -f "%e" ./$3 master $arg 2> ${LOGDIR}/tmp
    envtime=`cat ${LOGDIR}/tmp | grep -v Command`
    echo "$envtime" >> ~/.tmpenvs
    perl -x${TOPDIR} ${TOPDIR}/switch/fetch_stat.pl settings=switch/settings.txt
    cat ${LOGDIR}/tmp >> ${LOGDIR}/${3}_${2}Thrd_${EXTENSION}.txt
    if [ $i -eq 0 ];then echo "<h3> Benchmark=${3} Thread=${2} Extension=${EXTENSION}</h3><br>" > ${LOGDIR}/${3}_${EXTENSION}_${2}Thrd_a.html  ;fi
    cat ${LOGDIR}/tmp >> ${LOGDIR}/${3}_${EXTENSION}_${2}Thrd_a.html
    echo "<a href=\"${3}_${2}Thrd_${EXTENSION}_${i}.html\">Network Stats</a><br>" >> ${LOGDIR}/${3}_${EXTENSION}_${2}Thrd_a.html
    mv ${TOPDIR}/html/dell.html ${LOGDIR}/${3}_${2}Thrd_${EXTENSION}_${i}.html
    echo "Terminating ... "
    HOSTNAME=`hostname`
    for machine in `echo $MACHINES`
    do
      if [ "$machine" != "$HOSTNAME" ]; then
        ssh ${machine} '~/research/Robust/src/Benchmarks/Prefetch/killallclients.sh'
        #ssh ${machine} 'source ~/.tmpvars; binpid=`ps aux | grep $bin | grep time | sed 's/[     ]./:/g' | sed 's/::/:/g' | cut -f2 -d":"`;kill -USR1 $binpid'
      fi 
    done
    sleep 2
    i=`expr $i + 1`
  done
}

function oneremote {
  i=0;
  DIR=`pwd` 

  DSTMDIR=${HOME}/research/Robust/src/Benchmarks/Prefetch/config
  rm dstm.conf
  ln -s ${DSTMDIR}/dstm_2.conf dstm.conf

  while [ $i -lt $1 ]; do
    echo "$DIR" > ~/.tmpdir
    echo "bin=$3" > ~/.tmpvars
    echo "arg='$ARGS1'" > ~/.tmpargs
    echo "logd=$LOGDIR" > ~/.tmplogdir
    echo "ext=$EXTENSION" > ~/.tmpext
    ./$3 &
    ssh dc-2.calit2.uci.edu 'cd `cat ~/.tmpdir`; source ~/.tmpvars; source ~/.tmpargs; source ~/.tmplogdir; source ~/.tmpext; /usr/bin/time -f "%e" ./$bin master $arg 2>> ${logd}/${bin}_remote_${ext}.txt'
    echo "Terminating ... "
    killall $3
    sleep 2
    i=`expr $i + 1`
  done
}

function localrun {
  i=0;

  DSTMDIR=${HOME}/research/Robust/src/Benchmarks/Prefetch/config
  rm dstm.conf
  ln -s ${DSTMDIR}/dstm_1.conf dstm.conf

  while [ $i -lt $1 ]; do
    /usr/bin/time -f "%e" ./${NONPREFETCH_NONCACHE} master $ARGS1 2> ${LOGDIR}/tmp
    cat ${LOGDIR}/tmp >> ${LOGDIR}/${NONPREFETCH_NONCACHE}_local_${EXTENSION}.txt
    if [ $i -eq 0 ];then echo "<h3> Benchmark=${NONPREFETCH_NONCACHE} Thread=1local Extension=${EXTENSION}</h3><br>" > ${LOGDIR}/${NONPREFETCH_NONCACHE}_${EXTENSION}_1local_a.html  ;fi
    cat ${LOGDIR}/tmp >> ${LOGDIR}/${NONPREFETCH_NONCACHE}_${EXTENSION}_1local_a.html
    echo "<br>" >> ${LOGDIR}/${NONPREFETCH_NONCACHE}_${EXTENSION}_1local_a.html
    sleep 2
    i=`expr $i + 1`
  done
}

function callrun {
  PREFETCH=${BENCHMARK}N.bin
  NONPREFETCH=${BENCHMARK}NPC.bin
  NONPREFETCH_NONCACHE=${BENCHMARK}NPNC.bin
  MANUAL_PREFETCH=${BENCHMARK}RangeN.bin

  cd $BMDIR 

  echo "---------- Running local $BMDIR non-prefetch on 1 machine ---------- "
  localrun 10

#  echo "---------- Running single thread remote $BMDIR non-prefetch + non-cache on 2 machines ---------- "
#  oneremote 1 1 $NONPREFETCH_NONCACHE
#  echo "---------- Running single thread remote $BMDIR non-prefetch on 2 machines ---------- "
#  oneremote 1 1 $NONPREFETCH
#  echo "---------- Running single thread remote $BMDIR prefetch on 2 machines ---------- "
#  oneremote 1 1 $PREFETCH


for count in 2 4 8
do
    echo "------- Running $count threads $BMDIR non-prefetch + non-cache on $count machines -----"
    run 10 $count $NONPREFETCH_NONCACHE
    echo "------- Running $count threads $BMDIR non-prefetch on $count machines -----"
    run 10 $count $NONPREFETCH
    echo "------- Running $count threads $BMDIR normal prefetch on $count machines -----"
    run 10 $count $PREFETCH
    #echo "------- Running $count threads $BMDIR manual prefetch on $count machines -----"
    #run 10 $count $MANUAL_PREFETCH

###########
#  echo "------- Running $count threads $BMDIR non-prefetch + non-cache on $count machines -----"
# runallstats 1 $count $NONPREFETCH_NONCACHE
  #echo "------- Running $count threads $BMDIR non-prefetch on $count machines -----"
  #runallstats 1 $count $NONPREFETCH
#  echo "------- Running $count threads $BMDIR normal prefetch on $count machines -----"
# runallstats 1 $count $PREFETCH
  #echo "------- Running $count threads $BMDIR manual prefetch on $count machines -----"
  #runallstats 1 $count $MANUAL_PREFETCH
#############

done

cd $TOPDIR
}

function callrunjavasingle {
  JAVASINGLE=${BENCHMARK}.bin
  cd $BMDIR
  cd ../javasingle

  echo "-----------Running javasingle for ${BENCHMARK} version ${EXTENSION} on 1 machines ------------"
  javasinglerun 10
  cd $TOPDIR
}

function javasinglerun {
  i=0;
  while [ $i -lt $1 ]; do
    /usr/bin/time -f "%e" ./${BENCHMARK}.bin $ARGS1 2> ${LOGDIR}/tmp
    cat ${LOGDIR}/tmp >> ${LOGDIR}/${BENCHMARK}_javasingle_${EXTENSION}.txt
    if [ $i -eq 0 ];then echo "<h3> Benchmark=${BENCHMARK} Thread=1java Extension=${EXTENSION}</h3><br>" > ${LOGDIR}/${BENCHMARK}_${EXTENSION}_1java_a.html  ;fi
    cat ${LOGDIR}/tmp >> ${LOGDIR}/${BENCHMARK}_${EXTENSION}_1java_a.html
    echo "<br>" >> ${LOGDIR}/${BENCHMARK}_${EXTENSION}_1java_a.html
    sleep 2
    i=`expr $i + 1`
  done
}

function callmicrorun {
  PREFETCH=${BENCHMARK}N.bin
  NONPREFETCH=${BENCHMARK}NPC.bin
  NONPREFETCH_NONCACHE=${BENCHMARK}NPNC.bin
  MANUAL_PREFETCH=${BENCHMARK}RangeN.bin

  cd $BMDIR 
  echo "---------- Running local $BMDIR non-prefetch on 1 machine ---------- "
  localrun 10
  echo "---------- Running single thread remote $BMDIR non-prefetch + non-cache on 2 machines ---------- "
  oneremote 10 1 $NONPREFETCH_NONCACHE
#  echo "---------- Running single thread remote $BMDIR non-prefetch on 2 machines ---------- "
#  oneremote 10 1 $NONPREFETCH
  echo "---------- Running single thread remote $BMDIR prefetch on 2 machines ---------- "
  oneremote 10 1 $PREFETCH
#  echo "------- Running $count threads $BMDIR manual prefetch on $count machines -----"
#  oneremote 1 1 $MANUAL_PREFETCH
  cd $TOPDIR
}


echo "---------- Clean old files ---------- "
#rm runlog/*
mv runlog/* runlog/results/.
echo  "moving files"
for b in `echo $benchmarks`
do
  bm=`grep $b bm.txt`
  BENCHMARK=`echo $bm | cut -f1 -d":"`
  BMDIR=`echo $bm | cut -f2 -d":"`
  ARGS1=`echo $bm | cut -f3 -d":"`
  ARGS2=`echo $bm | cut -f4 -d":"`
  ARGS3=`echo $bm | cut -f5 -d":"`
  ARGS4=`echo $bm | cut -f6 -d":"`
  ARGS5=`echo $bm | cut -f7 -d":"`
  ARGS6=`echo $bm | cut -f8 -d":"`
  ARGS7=`echo $bm | cut -f9 -d":"`
  ARGS8=`echo $bm | cut -f10 -d":"`
  EXTENSION=`echo $bm | cut -f11 -d":"`
  name1='array'
  name2='chase'
  name3='tree'
  if [ $b == $name1 ] || [ $b == $name2 ] || [ $b == $name3 ]; then
  callmicrorun
  else
  callrunjavasingle
  callrun
  fi
done

#----------Calulates  the averages ----------- 
for file in `ls runlog/*.txt`
do
  echo -n $file >> average.txt
  cat $file | grep -v "^Command" | awk '{sum += $1} END {print " "sum/NR}' >> average.txt
done
echo "===========" >> average.txt
echo "" >> average.txt

echo "done"
