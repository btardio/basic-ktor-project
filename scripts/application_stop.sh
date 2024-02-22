#!/bin/bash
# docker-compose down

docker container stop application-httpdproxy-1 || true
docker container stop httpd || true
docker container stop application-rabbit-1 || true
docker container stop kotlin-ktor-analyzer || true
docker container stop kotlin-ktor-collector || true
docker container stop zoo1 || true
docker container stop kotlin-ktor-server || true
docker container stop solr2 || true
docker container stop solr3 || true
docker container stop solr1 || true

docker container rm application-httpdproxy-1 || true
docker container rm httpd || true
docker container rm application-rabbit-1 || true
docker container rm kotlin-ktor-analyzer || true
docker container rm kotlin-ktor-collector || true
docker container rm zoo1 || true
docker container rm kotlin-ktor-server || true
docker container rm solr2 || true
docker container rm solr3 || true
docker container rm solr1 || true