FROM gradle:jdk17



ENV PORT=8888
ENV JAVA_OPTS=""
ENV APP=""
COPY . .
#RUN ls
#RUN gradle build
EXPOSE ${port}
#ENTRYPOINT /bin/bash
ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${PORT} ${JAVA_OPTS} -jar ${APP}
