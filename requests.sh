#!/bin/bash

REQUESTS_NUM=$1

for ((request=1;request<=REQUESTS_NUM;request++))
do
    curl -s  curl localhost:8080/get-fortune && echo " <<== Request ${request} finished" &
done

wait