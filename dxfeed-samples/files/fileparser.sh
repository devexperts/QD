#!/bin/bash 
java -cp .:lib/dxfeed-samples.jar:lib/qds.jar:lib/qds-file.jar com.dxfeed.sample.api.DXFeedFileParser "$@"