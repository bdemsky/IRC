#!/bin/bash

num="0"

NAME[$num]=Bank
BDIR[$num]=BankApp
num=$[$num+1]

NAME[$num]=Chat
BDIR[$num]=ChatTag
num=$[$num+1]

NAME[$num]=WebPortal
BDIR[$num]=Conglomerator/Tag
num=$[$num+1]

NAME[$num]=jHTTPp2
BDIR[$num]=Jhttpp2/BR
num=$[$num+1]

NAME[$num]=MapReduce1
BDIR[$num]=MapReduce/Tag
num=$[$num+1]

NAME[$num]=MultiGame
BDIR[$num]=MMG/Tag
num=$[$num+1]

NAME[$num]=PERT
BDIR[$num]=PERT/Tag
num=$[$num+1]

NAME[$num]=FilterBank
BDIR[$num]=Scheduling/FilterBank
num=$[$num+1]

NAME[$num]=Fractal
BDIR[$num]=Scheduling/Fractal
num=$[$num+1]

NAME[$num]=MolDyn
BDIR[$num]=Scheduling/JGFMolDyn
num=$[$num+1]

NAME[$num]=MonteCarlo
BDIR[$num]=Scheduling/JGFMonteCarlo
num=$[$num+1]

NAME[$num]=Series
BDIR[$num]=Scheduling/JGFSeries
num=$[$num+1]

NAME[$num]=KMeans-Bamboo
BDIR[$num]=Scheduling/KMeans
num=$[$num+1]

NAME[$num]=MapReduce2
BDIR[$num]=Scheduling/MapReduce
num=$[$num+1]

NAME[$num]=FluidAnimate
BDIR[$num]=Scheduling/PSFluidAnimate
num=$[$num+1]

NAME[$num]=Spider1
BDIR[$num]=Spider/BR
num=$[$num+1]

NAME[$num]=Spider2
BDIR[$num]=Spider/BRTag
num=$[$num+1]

NAME[$num]=TileSearch
BDIR[$num]=TileSearch/Tag
num=$[$num+1]

NAME[$num]=TicTacToe
BDIR[$num]=TTTTag
num=$[$num+1]

NAME[$num]=WebServer1
BDIR[$num]=WebServer
num=$[$num+1]

NAME[$num]=WebServer2
BDIR[$num]=WebServerTag
num=$[$num+1]

NAME[$num]=Tracking
BDIR[$num]=Scheduling/Tracking
num=$[$num+1]



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
while [ $i -lt $num ]; do
  cd $BENCHTOP/${BDIR[$i]}
  # unfortunately this echo adds an unwanted newline
  echo ${NAME[$i]} >> $BENCHSUM/$TABFILE 
  make -f $BENCHSUM/makefile tabbed
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
