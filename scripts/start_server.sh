#!/bin/bash
# sudo yum install -y java-17-amazon-corretto-headless
# sudo /application/gradlew build
#docker build  /application/ -t kotlin-ktor-starter --no-cache

sudo rm -f /application/local.env
cp /application/environment-aws/$(echo ${IPADDRA}) /env
cp "/application/environment-aws/$IPADDRA" "/env"

docker-compose -f /application/docker-compose.yml --env-file /env down
docker-compose -f /application/docker-compose.yml --env-file /env build
docker-compose -f /application/docker-compose.yml --env-file /env up --detach
