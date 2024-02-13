#!/bin/bash
# sudo yum install -y java-17-amazon-corretto-headless
# sudo /application/gradlew build
#docker build  /application/ -t kotlin-ktor-starter --no-cache

source /ipaddr

rm -f /application/local.env

export IPADDRA=$IPADDRA
export IPADDRB=$IPADDRB
export IPADDRC=$IPADDRC

rm -f /env

sh /application/environment-aws/$(echo ${IPADDRA})
# cp -f "/application/environment-aws/$IPADDRA" "/env"

echo $IPADDRA

docker container stop httpd
docker container stop zoo3
docker container stop application-rabbit-1
docker container stop kotlin-ktor-analyzer
docker container stop zoo2
docker container stop kotlin-ktor-collector
docker container stop zoo1
docker container stop kotlin-ktor-server
docker container stop solr2
docker container stop solr3
docker container stop solr1

docker-compose -f /application/docker-compose.yml --env-file /env down
docker-compose -f /application/docker-compose.yml --env-file /env build
docker-compose -f /application/docker-compose.yml --env-file /env up --detach
