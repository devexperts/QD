#!/bin/bash 
java -cp .:lib/dxfeed-samples.jar:lib/qds.jar com.dxfeed.sample.api.DXFeedConnect "$@"