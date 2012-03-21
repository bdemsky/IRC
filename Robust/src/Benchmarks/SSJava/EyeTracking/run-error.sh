#!/bin/bash

LEAe.bin > error.txt

grep "SSJAVA: Injecting error" error.txt

diff normal.txt error.txt