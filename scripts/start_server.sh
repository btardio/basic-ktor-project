#!/bin/bash
sudo yum install -y java-17-amazon-corretto-headless
./gradlew build
# docker-compose up
