#!/bin/bash

NAME[0]=Bank
BDIR[0]=BankApp

NAME[1]=Chat
BDIR[1]=ChatTag

NAME[2]=Conglomerator
BDIR[2]=Conglomerator/Tag

NAME[3]=jHTTPp2
BDIR[3]=Jhttpp2/BR

NAME[4]=MapReduce1
BDIR[4]=MapReduce/Tag

NAME[5]=MultiGame
BDIR[5]=MMG/Tag

NAME[6]=Performance
BDIR[6]=Performance

NAME[7]=PERT
BDIR[7]=PERT/Tag

NAME[8]=FilterBank
BDIR[8]=Scheduling/FilterBank

NAME[9]=Fractal
BDIR[9]=Scheduling/Fractal

NAME[10]=MolDynamics
BDIR[10]=Scheduling/JGFMolDyn

NAME[11]=MonteCarlo
BDIR[11]=Scheduling/JGFMonteCarlo

NAME[12]=Series
BDIR[12]=Scheduling/JGFSeries

NAME[13]=KMeans
BDIR[13]=Scheduling/KMeans

NAME[14]=MapReduce2
BDIR[14]=Scheduling/MapReduce

NAME[15]=FluidAnimate
BDIR[15]=Scheduling/PSFluidAnimate

NAME[16]=Spider1
BDIR[16]=Spider/BR

NAME[17]=Spider2
BDIR[17]=Spider/BRTag

NAME[18]=TileSearch
BDIR[18]=TileSearch/Tag

NAME[19]=TicTacToe
BDIR[19]=TTTTag

NAME[20]=WebServer1
BDIR[20]=WebServer

NAME[21]=WebServer2
BDIR[21]=WebServerTag

NUMBENCHMARKS=22



###########################
# No need to modify below!
###########################

BENCHTOP=~/research/Robust/src/Benchmarks
BENCHSUM=$BENCHTOP/Ownership

TABFILE=tabResults.tex
rm -f $TABFILE
touch $TABFILE
echo '\begin{tabular}{|l|l|r|r|r|}'                        >> $TABFILE
echo '\hline'                                              >> $TABFILE
echo 'Benchmark & Sharing & Time (s) & Lines & Methods \\' >> $TABFILE
echo '\hline'                                              >> $TABFILE

i="0"
while [ $i -lt $NUMBENCHMARKS ]; do
  cd $BENCHTOP/${BDIR[$i]}
  # unfortunately this echo adds an unwanted newline
  echo ${NAME[$i]} >> $BENCHSUM/$TABFILE 
  make -f $BENCHSUM/makefile
  cat aliases.txt >> $BENCHSUM/$TABFILE
  make -f $BENCHSUM/makefile clean
  i=$[$i+1]
done

cd $BENCHSUM

echo '\hline'        >> $TABFILE
echo '\end{tabular}' >> $TABFILE

# remove unwanted newlines from file so latex doesn't barf
sed '
/$/ {
# append the next line
	N
# look for multi-line pattern
	/\n \&/ {
#	delete everything between
		s/\n \&/ \&/
#	print
		P
#	then delete the first line
		D
	}
}' <$TABFILE >$TABFILE.temp
mv $TABFILE.temp $TABFILE
