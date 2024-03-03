#!/usr/local/bin/python3

import docker
import redis
import time

r = redis.Redis(host='localhost', port=6379, db=0)

client = docker.from_env()


server = client.containers.get("kotlin-ktor-server")
collector = client.containers.get("kotlin-ktor-analyzer")
analyzer = client.containers.get("kotlin-ktor-collector")

analyzerkey = "analyzer" #-queue-ip-10-0-1-200.us-west-1.compute.internal-a"
collectorkey = "collector" #-queue-ip-10.0.1-200.us-west-1.compute.internal-b"
webserverkey = "webserver" #-queue-ip-10.0.1-200.us-west-1.compute.internal-a"

# 1) "collector-queue-ip-10-0-1-200.us-west-1.compute.internal-b"
# 2) "webserver-queue-ip-10-0-1-200.us-west-1.compute.internal-a"
# 3) "analyzer-queue-ip-10-0-1-200.us-west-1.compute.internal-a"

while (True):
    analyzerkeyvalue = r.get(analyzerkey)
    collectorkeyvalue = r.get(collectorkey)
    webserverkeyvalue = r.get(webserverkey)
#    print(analyzerkeyvalue)
#    print(collectorkeyvalue)
#    print(webserverkeyvalue)

    if ( analyzerkeyvalue == None ):
        analyzer.restart(timeout=3)

    if ( collectorkeyvalue == None ):
        collector.restart(timeout=3)

    if ( webserverkeyvalue == None ):
        server.restart(timeout=3)

    time.sleep(3)
