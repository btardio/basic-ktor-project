# Project K-Means

[//]: # (An app for verifying email addresses in a registration flow, which is)

[//]: # (designed to handle very high throughput.)


## Set up

### Local Development (Product Environment)

The local.env file should be sufficient to start up the necessary services in
the docker-compose.yml file. Start up these services using the command:

```
docker compose --env-file=./local.env up --detach
```

Each of the three: collector, analyzer and webserver is started in an infinite
loop to keep them from restarting. 

```
while true; do echo 'ACK'; sleep 1; done"
```

These do no currently start the application. Local developers can exec
into the services and mount the volume to integrate with the IDE. The clean
and build step can be skipped.

Collector:
```
./gradlew clean && 
./gradlew build && 
java -jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 /collector/collector/build/libs/collector.jar

```

Webserver:
```
./gradlew clean && 
./gradlew build && 
java -jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 /webserver/webserver/build/libs/webserver.jar
```

Analyzer:
```
./gradlew clean && 
./gradlew build && 
java -jar -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 /analyzer/analyzer/build/libs/analyzer.jar
```

### Running the Application (Product Environment & Rest Collaboration internal or API endpoint)

The application comes with a very minimal JQuery frontend located in the httpd/htdocs/index.html directory.

#

Exposed endpoints are:

http://netty.netoxena.com/startKmeans/{filename}.png

This endpoint starts a kmeans job for one of the predefined pictures.

#

http://netty.netoxena.com/getAllSchedules

This endpoint sends a request to the back end to return all jobs in the last 15 minutes.

### Data persistence

#### Solr
The application uses Solr, a document database to store the pixel values of the images.
This is a horizontally scaling database and uses Zookeeper for coordination of replicas 
shards and collections. 

Observations working with Solr were that it doesn't work anymore once a replica or shard isn't
available, and it doesn't recover. It was my observation that it likes many replicas and many
shards and the larger the better. This is a repeated observation working with 
Rabbit, Solr and Zookeeper.

There are three collections, one collection for each the collector, the analyzer and the webserver.

Further improvements are the clean up the records once they are completed in the system and
save the a vertically scaling database.

#### Redis
Redis is also used in the project, chosen because it has an EXPIRE instruction and is fairly
straightforward and simple to work with. This is used to keep the individual rabbit queues 
going, it was observed that they sometimes fail and their queues get backed up. The collector
analyzer and webserver send a SET request to redis that expires in 60 seconds, if the collector
analyzer and webserver aren't succeeding consuming records on their queue they are restarted.

This strategy seems to work very well and further improvements could be made to have more than 1 
analyzer collector webserver ready for failures.

It is my guess that Solr is implemented keeping it highly available by periodically dumping long 
lasting or stale connections.

### Unit tests

There is one large unit test using test containers for each of the collector, analyzer and webserver.

The test has greater than 80% code coverage for the consumer.

This is an area that could be further worked on and need improvement.

### Analyzer, Collector, Server

The analyzer collector and server share a similar architecture. Read from the previous 
exchange the schedule, read from solr the RGB coordinates, perform some processing on the 
RGB coordinates and write to the next Solr collection.

Rabbit messages pass through the system starting at the data server, progressing to the collector
and then the analyzer and finally back to the data server. 

Consistency is handled in all three by simply publishing back to the same queue that was consumed
the message that didn't yet have the coordinates available on the Solr database.

The data format chosen was json and for simplicity to complete the project on time the same
serializable records is used for solr. This format has two uuids and jsonData. This was also picked
because the indexed value is a uuid.

#### Data Analyzer

The data analyzer is written in Kotlin, Java and Scala. I reused an assignment from a course
online moderated by EFPL University Switzerland. The image indexing algorithm uses k-means.

#### Data Collector

The data collector collects an image and converts the image into pixel RGB XYZ values between 0 and 1.

#### Data Server

The data server coordinates start of a schedule and end of a schedule, also providing REST endpoints.
It could be an improvement to further separate the Netty from the Rabbit consumer.

### Integration Tests

A simple integration test written in Python tests the system and makes sure that all records
return successfully and record the time that it takes them to return. This integration
test is found in the root directory: keepalive.py

Improvements to this include building it bigger to see how the system handles at load.








### AWS

```

    docker compose --env-file /.env build
     
    docker compose --env-file=/.env up --detach


```

Read logs of deploy live.
```
tail -f /opt/codedeploy-agent/deployment-root/deployment-logs/codedeploy-agent-deployments.log
```


[//]: # ()
[//]: # (1.  Run migrations)

[//]: # (    ```shell)

[//]: # (    ./gradlew devMigrate testMigrate)

[//]: # (    ```)

[//]: # ()
[//]: # (## Build and run)

[//]: # (    )
[//]: # (1.  Use the [Gradle Kotlin plugin]&#40;https://kotlinlang.org/docs/gradle.html#compiler-options&#41;)

[//]: # (    to run tests, build, and fetch dependencies.)

[//]: # (    For example, to build run)

[//]: # (    ```shell)

[//]: # (    ./gradlew build)

[//]: # (    ```)

[//]: # ()
[//]: # (1.  Run the notification server.)

[//]: # (    ```shell)

[//]: # (    ./gradlew applications:notification-server:run)

[//]: # (    ```)

[//]: # (    )
[//]: # (    Luckily, Gradle fuzzy-matches task names, so the command can optionally be shortened to)

[//]: # ()
[//]: # (    ```shell)

[//]: # (    ./gradlew a:n:r)

[//]: # (    ```)

[//]: # ()
[//]: # (1.  Run the registration server in a separate terminal window.)

[//]: # (    ```shell)

[//]: # (    ./gradlew applications:registration-server:run)

[//]: # (    ```)

[//]: # (    )
[//]: # (1.  Run the fake Sendgrid server in another separate terminal window.)

[//]: # (    ```shell)

[//]: # (    ./gradlew platform-support:fake-sendgrid:run)

[//]: # (    ```)

[//]: # ()
[//]: # (## Make requests)

[//]: # ()
[//]: # (1.  Post to [http://localhost:8081/request-registration]&#40;http://localhost:8081/request-registration&#41;)

[//]: # (    to make a registration request.)

[//]: # (    Include the email address to register in the request body.)

[//]: # (    ```json)

[//]: # (    {)

[//]: # (      "email": "jenny@example.com")

[//]: # (    })

[//]: # (    ```)

[//]: # ()
[//]: # (    Don't forget to add the content type header.)

[//]: # (    ```text)

[//]: # (    Content-Type: application/json)

[//]: # (    ```)

[//]: # (    )
[//]: # (1.  Check the logs of the fake Sendgrid server for your confirmation code.)

[//]: # (    Once you receive it, post to [http://localhost:8081/register]&#40;http://localhost:8081/register&#41;)

[//]: # (    to confirm your registration.)

[//]: # (    Include your email address and confirmation code in the request body.)

[//]: # (    ```json)

[//]: # (    {)

[//]: # (        "email": "jenny@example.com",)

[//]: # (        "confirmationCode": "18675309-1234-5678-90ab-cdef00000000")

[//]: # (    })

[//]: # (    ```)

[//]: # ()
[//]: # (    Don't forget to add the content type header.)

[//]: # (    ```text)

[//]: # (    Content-Type: application/json)

[//]: # (    ```)

[//]: # ()
[//]: # (See the `requests.http` file for sample requests)

[//]: # ()
[//]: # (## Benchmarks)

[//]: # ()
[//]: # (The _benchmark app_ runs a simple benchmark test against the running apps.)

[//]: # ()
[//]: # (1.  Stop the fake Sendgrid app, then run the benchmark app with)

[//]: # (    ```shell)

[//]: # (    ./gradlew applications:benchmark:run)

[//]: # (    ```)

[//]: # ()
[//]: # (    This will send some traffic to the notification and registration servers, and will print some basic metrics to the)

[//]: # (    console.)

[//]: # ()
[//]: # (1.  Once the benchmark is finished, try running it again giving different values for the `REGISTRATION_COUNT`,)

[//]: # (    `REGISTRATION_WORKER_COUNT`, and `REQUEST_WORKER_COUNT` environment variables.)

[//]: # (    )
[//]: # (1.  After getting comfortable with the environment, try running multiple instances of the notification server and the)

[//]: # (    registration server.)

[//]: # (    Make sure to provide a unique `PORT` environment variable to each instance of the registration server.)

[//]: # ()
[//]: # (## Consistent hash exchange)

[//]: # ()
[//]: # (Now that we have our system working with multiple instances, we will implement a [consistent hash exchange]&#40;https://github.com/rabbitmq/rabbitmq-server/tree/master/deps/rabbitmq_consistent_hash_exchange&#41;)

[//]: # (to better distribute load between our registration request consumers.)

[//]: # (Look for the `TODO`s in the codebase to help you get started.)
