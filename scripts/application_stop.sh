#!/bin/bash
# docker-compose down

docker container stop application-httpdproxy-1
docker container stop httpd
docker container stop application-rabbit-1
docker container stop kotlin-ktor-analyzer
docker container stop kotlin-ktor-collector
docker container stop zoo1
docker container stop kotlin-ktor-server
docker container stop solr2
docker container stop solr3
docker container stop solr1

docker container rm application-httpdproxy-1
docker container rm httpd
docker container rm application-rabbit-1
docker container rm kotlin-ktor-analyzer
docker container rm kotlin-ktor-collector
docker container rm zoo1
docker container rm kotlin-ktor-server
docker container rm solr2
docker container rm solr3
docker container rm solr1