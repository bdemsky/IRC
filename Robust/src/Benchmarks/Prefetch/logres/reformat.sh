#!/bin/sh

benchmarks='fft2d Convolution JGFMolDynBenchSizeB MatrixMultiply RainForest SpamFilter JGFSORBenchSizeD'
FILENAME=res.txt
cond=`expr 8 \+ 1`
rm res.txt
#echo "++Benchmark---NumThread---ExecTimeNPNC---ExecTimeN---PrefetchImprov%---SpeedUp++" >> $FILENAME

for bm in $benchmarks
do
    t1=0
    t1=`grep ${bm}_javasingle average.txt | awk '{print $2}'`
    echo "${bm} 0 $t1 -1 -1" >>  $FILENAME
    t2=0
    t2=`grep ${bm}NPNC.bin_local average.txt | awk '{print $2}'`
    echo "${bm} 1 $t2 -1 -1" >>  $FILENAME
    thrdid=2
    while [ "${thrdid}" -lt "$cond" ] 
      do
        t3=`grep ${bm}N.bin_${thrdid}Thrd average.txt | awk '{print $2}'`
        t4=`grep ${bm}NPNC.bin_${thrdid}Thrd average.txt | awk '{print $2}'`
        t5=`echo "scale=2; ((${t4} - ${t3}) / ${t4}) * 100" | bc`
        t6=0
        if [ ${thrdid} -eq 8 ]; then
          t6=`echo "scale=2; (${t1} / ${t3})" | bc`
        fi
        echo "${bm} ${thrdid} $t4 $t3 $t5 $t6" >> $FILENAME
        thrdid=`expr $thrdid \+ $thrdid`
      done
done
