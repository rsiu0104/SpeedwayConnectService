# SpeedwayConnect Service

SpeedwayConnect is the middle-ware for Impinj R220/R420 fixed readers. The middle-ware can be configured to send events via HTTP Post.

This service is design to consume the HTTP Post and put the event on the Event Bus.


## Build

```
mvn clean package
```

## Run

```
java -jar target\SpeedwayConnectService-1.0-SNAPSHOT.original.jar --cluster -Djava.net.preferIPv4Stack=true
```
## Run with Debug

```
java -Djava.util.logging.config.file=src\conf\logging.properties -jar target\SpeedwayConnectService-1.0-SNAPSHOT.original.jar --cluster -Djava.net.preferIPv4Stack=true -conf src\conf\config.json
```
