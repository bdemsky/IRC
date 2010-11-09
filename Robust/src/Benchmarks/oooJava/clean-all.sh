#!/bin/bash

# list all the files in the directory
# containing benchmark directories
for i in $( ls ); 
do
  # only operate on directories
  if [ -d "$i" ] ; then
    # ignore the CVS directory
    if [ "$i" != "CVS" ] ; then
      # go in, make clean, get out
      cd $i
      pwd
      make clean
      cd ..
    fi
  fi
done
