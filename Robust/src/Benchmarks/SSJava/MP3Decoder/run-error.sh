#!/bin/bash
MP3Playere.bin focus.mp3 > error.txt

grep "SSJAVA: Injecting error" error.txt

mp3samples2plotData.sh error.txt