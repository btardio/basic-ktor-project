#!/bin/bash
# sudo yum install -y java-17-amazon-corretto-headless
# sudo /application/gradlew build
#docker build  /application/ -t kotlin-ktor-starter --no-cache

source /root/.bashrc

rm -f /application/local.env

export IPADDRA=$IPADDRA
export IPADDRB=$IPADDRB
export IPADDRC=$IPADDRC

rm -f /env

sh /application/environment-aws/$(echo ${IPADDRA})
# cp -f "/application/environment-aws/$IPADDRA" "/env"

echo $IPADDRA

docker-compose -f /application/docker-compose.yml --env-file /env down
docker-compose -f /application/docker-compose.yml --env-file /env build
docker-compose -f /application/docker-compose.yml --env-file /env up --detach
