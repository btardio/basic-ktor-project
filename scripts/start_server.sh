#!/bin/bash
# sudo yum install -y java-17-amazon-corretto-headless
# sudo /application/gradlew build
docker build  /application/ -t kotlin-ktor-starter --no-cache
docker-compose -f /application/docker-compose.yaml --env-file /.env down
docker-compose -f /application/docker-compose.yaml --env-file /.env up --detach
