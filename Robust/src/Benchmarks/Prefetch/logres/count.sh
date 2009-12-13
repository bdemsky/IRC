#!/bin/sh
printhelp() {
	echo "Usage: ./count.sh < Input"
}

MAX_COUNT=`expr 8 \+ 1`

INPUT_DIR=`pwd`
RESULT_DIR=/tmp/adash/prefetch_rst
OUTPUT=totalrst
OUTPUTHELP=helper
OUTPUTPREFETCH=prefetchrate

FILE_NPNC=''
FILE_N=''
RR_NPNC=0
PH_NPNC=0
EXE_NPNC=0
RR_N=0
PH_N=0
EXE_N=0
IMPROVEMENT=0
PREFETCHRATE=0
A_NPNC=0
SA_NPNC=0
C_NPNC=0
AR_NPNC=0
TA_NPNC=0
A_N=0
SA_N=0
C_N=0
AR_N=0
TA_N=0

count=0
thd=0
mid=0
max_mid=0

if [ ! -d ${RESULT_DIR} ] ; then
	mkdir ${RESULT_DIR}
fi

echo "Start"

while read bench compileop ; do
    echo "$bench"
	echo "==== $bench ==== $compileop ===="  >> ${RESULT_DIR}/${OUTPUTHELP}_${bench}.txt
	echo -e "# THREAD\tNPNC-RemoteRead\tNPNC-EXETime\tNPNC-Abort\tNPNC-Commit\t% NPNC-Abort\tN-RemoteRead\tN-PrefetchHit\tN-EXETime\tN-Abort\tN-Commit\t% N-Abort\t% Improvement\t% PrefetchHit"  >> ${RESULT_DIR}/${OUTPUT}_${bench}.txt
	count=2

	while [ "${count}" -lt "${MAX_COUNT}" ] 
	do
		echo "++++ $count ++++"  >> ${RESULT_DIR}/${OUTPUTHELP}_${bench}.txt
		RR_NPNC=0
		PH_NPNC=0
		EXE_NPNC=0
		RR_N=0
		PH_N=0
		EXE_N=0
		IMPROVEMENT=0
		PREFETCHRATE=0
		A_NPNC=0
		SA_NPNC=0
		C_NPNC=0
		AR_NPNC=0
		TA_NPNC=0
		A_N=0
		SA_N=0
		C_N=0
		AR_N=0
		TA_N=0

		thd=${count}
		mid=2
		max_mid=`expr ${count} \+ 1`
		while [ "${mid}" -lt "${max_mid}" ] 
		do
			FILE_NPNC=${bench}NPNC.bin_${count}_${compileop}_thd_${thd}_dc-${mid}.txt
			FILE_N=${bench}N.bin_${count}_${compileop}_thd_${thd}_dc-${mid}.txt

			rrnpnc=`grep 'nRemoteReadSend' ${INPUT_DIR}/${FILE_NPNC} | awk '{print $3}'`
			RR_NPNC=`echo "scale=0; ${RR_NPNC} + ${rrnpnc}" | bc`
			phnpnc=`grep 'nprehashSearch' ${INPUT_DIR}/${FILE_NPNC} | awk '{print $3}'`
			PH_NPNC=`echo "scale=0; ${PH_NPNC} + ${phnpnc}" | bc`
			exenpnc=`grep 'executionTime' ${INPUT_DIR}/${FILE_NPNC} | awk '{print $3}'`
			EXE_NPNC=`echo "scale=4; ${EXE_NPNC} + ${exenpnc}" | bc`
			anpnc=`grep 'numTransAbort' ${INPUT_DIR}/${FILE_NPNC} | awk '{print $3}'`
			A_NPNC=`echo "scale=0; ${A_NPNC} + ${anpnc}" | bc`
			sanpnc=`grep 'nSoftAbort' ${INPUT_DIR}/${FILE_NPNC} | awk '{print $3}'`
			SA_NPNC=`echo "scale=0; ${SA_NPNC} + ${sanpnc}" | bc`
			cnpnc=`grep 'numTransCommit' ${INPUT_DIR}/${FILE_NPNC} | awk '{print $3}'`
			C_NPNC=`echo "scale=0; ${C_NPNC} + ${cnpnc}" | bc`

			rrn=`grep 'nRemoteReadSend' ${INPUT_DIR}/${FILE_N} | awk '{print $3}'`
			RR_N=`echo "scale=0; ${RR_N} + ${rrn}" | bc`
			phn=`grep 'nprehashSearch' ${INPUT_DIR}/${FILE_N} | awk '{print $3}'`
			PH_N=`echo "scale=0; ${PH_N} + ${phn}" | bc`
			exen=`grep 'executionTime' ${INPUT_DIR}/${FILE_N} | awk '{print $3}'`
			EXE_N=`echo "scale=4; ${EXE_N} + ${exen}" | bc`
			an=`grep 'numTransAbort' ${INPUT_DIR}/${FILE_N} | awk '{print $3}'`
			A_N=`echo "scale=0; ${A_N} + ${an}" | bc`
			san=`grep 'nSoftAbort' ${INPUT_DIR}/${FILE_N} | awk '{print $3}'`
			SA_N=`echo "scale=0; ${SA_N} + ${san}" | bc`
			cn=`grep 'numTransCommit' ${INPUT_DIR}/${FILE_N} | awk '{print $3}'`
			C_N=`echo "scale=0; ${C_N} + ${cn}" | bc`

			echo "mid: $mid, rrnpnc: $rrnpnc, phnpnc: $phnpnc, exenpnc: $exenpnc, anpnc: $anpnc, sanpnc: $sanpnc, cnpnc: $cnpnc, rrn: $rrn, phn: $phn, exen: $exen, an: $an, san: $san, cn: $cn"  >> ${RESULT_DIR}/${OUTPUTHELP}_${bench}.txt

			mid=`expr ${mid} \+ 1`
		done

		todiv=`expr ${count} \- 1`
		RR_NPNC=`echo "scale=0; ${RR_NPNC} / ${todiv}" | bc`
		PH_NPNC=`echo "scale=0; ${PH_NPNC} / ${todiv}" | bc`
		EXE_NPNC=`echo "scale=4; ${EXE_NPNC} / ${todiv}" | bc`
		A_NPNC=`echo "scale=0; ${A_NPNC} / ${todiv}" | bc`
		SA_NPNC=`echo "scale=0; ${SA_NPNC} / ${todiv}" | bc`
		C_NPNC=`echo "scale=0; ${C_NPNC} / ${todiv}" | bc`

		RR_N=`echo "scale=0; ${RR_N} / ${todiv}" | bc`
		PH_N=`echo "scale=0; ${PH_N} / ${todiv}" | bc`
		EXE_N=`echo "scale=4; ${EXE_N} / ${todiv}" | bc`
		A_N=`echo "scale=0; ${A_N} / ${todiv}" | bc`
		SA_N=`echo "scale=0; ${SA_N} / ${todiv}" | bc`
		C_N=`echo "scale=0; ${C_N} / ${todiv}" | bc`


		echo "todiv: $todiv, RR_NPNC: $RR_NPNC, PH_NPNC: $PH_NPNC, EXE_NPNC: $EXE_NPNC, A_NPNC: $A_NPNC, SA_NPNC: $SA_NPNC, C_NPNC:$C_NPNC, RR_N: $RR_N, PH_N: $PH_N, EXE_N: $EXE_N, A_N: $A_N, SA_N: $SA_N, C_N: $C_N"  >> ${RESULT_DIR}/${OUTPUTHELP}_${bench}.txt
		echo "+++++++++++"  >> ${RESULT_DIR}/${OUTPUTHELP}_${bench}.txt

        IMPROVEMENT=`echo "scale=4; ${EXE_NPNC} - ${EXE_N}" | bc`
		IMPROVEMENT=`echo "scale=2; ${IMPROVEMENT} * 100 / ${EXE_NPNC}" | bc`
		PREFETCHRATE=`echo "scale=2; ${PH_N} * 100 / ${RR_NPNC}" | bc`
        tmpvar=`echo "${A_NPNC} + ${C_NPNC}" | bc`
		AR_NPNC=`echo "scale=2; ${A_NPNC} * 100 / $tmpvar " | bc`
		TA_NAPC=`echo "scale=0; ${A_NPNC} + ${SA_NPNC}" | bc`
		AR_N=`echo "scale=2; ${A_N} * 100 / (${A_N} + ${C_N})" | bc`
		TA_N=`echo "scale=0; ${A_N} + ${SA_N}" | bc`
	
		echo -e "${thd}\t${RR_NPNC}\t${EXE_NPNC}\t${TA_NPNC}\t${C_NPNC}\t${AR_NPNC}\t${RR_N}\t${PH_N}\t${EXE_N}\t${TA_N}\t${C_N}\t${AR_N}\t${IMPROVEMENT}\t${PREFETCHRATE}"  >> ${RESULT_DIR}/${OUTPUT}_${bench}.txt

		echo -e "${PREFETCHRATE}"  >> ${RESULT_DIR}/${OUTPUTPREFETCH}_thd_${count}.txt
		
		count=`expr ${count} \* 2`
	done
done
echo "Finish"
