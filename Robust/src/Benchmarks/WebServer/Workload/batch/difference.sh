#!/bin/sh

rm result_inventory
#For every trans_inventory* file remove the lines starting with pre and sort the file
for file in `ls trans_inventory*`
do
	echo "Doing $file"
	grep -v pre $file > ${file}.nopre
	sort -n ${file}.nopre > runs/${file}.sorted
done

\rm *.nopre
cd runs
let x=0;
#for every sorted file created above diff it with the orginial file called pure/trans_inventory.sorted and print the total success or failures occured
for file in `ls *sorted`
do
	echo -n "Diffing $file...";
	diff $file ../pure/trans_inventory.sorted
	if [ $? -ne 0 ]
	then
		let "x+=1";
	else
		echo " success ";
	fi
done

echo "RESULT: x is $x";
echo -n "RESULT: Total files compared is "
ls *sorted | wc -l 

cd ..
