FROM openjdk:8-jre-alpine

ENV VERTICLE_FILE SpeedwayConnectService-1.0-SNAPSHOT.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8083

# Copy your fat jar to the container
COPY target/$VERTICLE_FILE $VERTICLE_HOME/

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec java -Djava.util.logging.config.file=src\conf\logging.properties -jar $VERTICLE_FILE"]