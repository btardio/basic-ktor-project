FROM openjdk:17-jdk-slim
ENV PORT=8888
ENV JAVA_OPTS=""
ENV APP=""
COPY . .
RUN ./gradlew build
EXPOSE ${port}
ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${PORT} ${JAVA_OPTS} -jar ${APP}
