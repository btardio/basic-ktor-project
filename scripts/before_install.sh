#!/bin/bash

rm -f /application/local.env

docker container stop application-httpdproxy-1 &>/dev/null || true
docker container stop httpd &>/dev/null  || true
docker container stop application-rabbit-1 &>/dev/null || true
docker container stop kotlin-ktor-analyzer &>/dev/null || true
docker container stop kotlin-ktor-collector &>/dev/null || true
docker container stop zoo1 &>/dev/null || true
docker container stop kotlin-ktor-server &>/dev/null || true
docker container stop solr2 &>/dev/null || true
docker container stop solr3 &>/dev/null || true
docker container stop solr1 &>/dev/null || true
docker container stop prometheus &>/dev/null || true
docker container stop grafana &>/dev/null || true

docker container rm application-httpdproxy-1 &>/dev/null || true
docker container rm httpd &>/dev/null || true
docker container rm application-rabbit-1 &>/dev/null || true
docker container rm kotlin-ktor-analyzer &>/dev/null || true
docker container rm kotlin-ktor-collector &>/dev/null || true
docker container rm zoo1 &>/dev/null || true
docker container rm kotlin-ktor-server &>/dev/null || true
docker container rm solr2 &>/dev/null || true
docker container rm solr3 &>/dev/null || true
docker container rm solr1 &>/dev/null || true
docker container rm prometheus &>/dev/null || true
docker container rm grafana &>/dev/null || true