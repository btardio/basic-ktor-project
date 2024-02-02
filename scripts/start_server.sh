#!/bin/bash
# sudo yum install -y java-17-amazon-corretto-headless
# sudo /application/gradlew build
docker build /application/ -t kotlin-ktor-starter
docker-compose -f /application/docker-compose.yaml down
export $(grep -v '^#' .env | xargs -d '\n')
docker-compose -f /application/docker-compose.yaml up --detach
