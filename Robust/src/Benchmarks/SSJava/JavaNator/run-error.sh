#!/bin/bash

RobotMaine.bin > error.txt

grep "SSJAVA: Injecting error" error.txt

#mp3samples2plotData.sh error.txt