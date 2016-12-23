#!/bin/bash 
java -cp .:lib/dxfeed-samples.jar:lib/qds.jar com.dxfeed.sample._simple_.$1 "$2" "$3"