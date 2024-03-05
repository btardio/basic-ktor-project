#!/bin/bash

source /ipaddr


export IPADDRA=$IPADDRA
export IPADDRB=$IPADDRB
export IPADDRC=$IPADDRC

rm -f /env

sh /application/environment-aws/$(echo ${IPADDRA})


docker-compose -f /application/docker-compose.aws.yml --env-file /env down
docker-compose -f /application/docker-compose.aws.yml --env-file /env build
docker-compose -f /application/docker-compose.aws.yml --env-file /env up --detach
