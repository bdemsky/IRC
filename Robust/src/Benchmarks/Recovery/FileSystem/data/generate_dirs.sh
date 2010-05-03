##### This file creates empty directories under /home/<adash>/<filename>/....... ####
### first generate the directories before creating files under it #####
### To run this script: ./generate_dirs.sh creates.txt, where creates.txt in the file with the list of directories to be created #######

#!/bin/bash

# Read from $1 and create dirs

file=$1

for line in `cat $file | awk '{print $2}'`
do
  dir=`echo $line | grep -v 'file[0-9]*'`
  if [ $? -eq 0 ];
  then
    echo "Creating $dir "
    mkdir -p $dir
  fi
done
