package hk.com.quantum.beijing;

import io.netty.handler.codec.http.HttpHeaders;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.Base64;
import java.util.HashMap;

public class SpeedwayConnectService extends AbstractVerticle {

    //Logging
    private static final Logger logger = LoggerFactory.getLogger(SpeedwayConnectService.class.getName());
    private WebClient client;
    private String credentials;
    private HashMap<String, String> ReaderAlarmIPHashMap;
    private JsonArray assetList;
    private static int AlarmDurationIn100ms;

    @Override
    public void start() throws Exception {

        AlarmDurationIn100ms = config().getInteger("alarm.durationInSec", 10) * 10;

        // Initialize HTTP Client to connect to Alarm IOT Device
//        client = vertx.createHttpClient();
        credentials = String.format("%s:%s", config().getString("alarm.user"), config().getString("alarm.password"));
        client = WebClient.create(vertx);



        // API Endpoint that receives HTTP Post from Impinj Readers
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/api/v1/reader/SpeedwayConnect/:location").handler(this::SWCHandler);
        router.post("/api/v1/reader/alert").handler(this::SWCAlertHandler);
        router.put("/api/v1/reader/TSL/:location").handler(this::TSLHandler);
//        String url = "/api/entity?filter=true&type=$tAsset&attribute=$aLocation&operator=eq&value=All";
        router.get("/api/entity").handler(this::GetAssetListHandler);
        router.get("/api").handler(this::TestLoginHandler);
        router.get("/api/currentuser").handler(this::TestLoginHandler);
        router.get("/api/v1/test").handler(this::TestHandler);
        vertx.createHttpServer().requestHandler(router::accept).listen(config().getInteger("http.port", 8083));

        // EventBus to get ReaderAlarmIP
        EventBus eb = vertx.eventBus();
        MessageConsumer<JsonArray> consumer = eb.consumer("ReaderInfo");
        consumer.handler(message -> {
            assetList = new JsonArray(message.body().toString());
            ReaderAlarmIPHashMap = getReaderAlarmIPHashMap(assetList);
            logger.info("ReaderInfo Eventbus: Received " + ReaderAlarmIPHashMap.size() + " record(s)");
            message.reply(ReaderAlarmIPHashMap.size());
        });

        consumer.completionHandler(res -> {
            if (res.succeeded()) {
                logger.info("The handler registration has reached all nodes");
            } else {
                logger.error("Registration failed!");
            }
        });
    }

    private void TestLoginHandler(RoutingContext routingContext) {
        routingContext.response()
                .putHeader("content-type", "application/json")
                .end("{}");  //TODO: Should relay the login to CS.
    }

    private void GetAssetListHandler(RoutingContext routingContext) {
        routingContext.response()
                .putHeader("content-type", "application/json")
                .end(assetList.toString());
//                .setStatusCode((assetList.size() == 0) ? 500 : 200)
//                .end((assetList.size() == 0) ? "{}" : assetList.toString());
    }

    private void TestHandler(RoutingContext routingContext) {
        routingContext.response()
                .putHeader("content-type", "text/plain")
                .end("Hello World! Version 1.0");
        logger.info("Test Page Accessed.");
    }

    private void SWCHandler(RoutingContext routingContext) {
        String location = "";
        String reader_name = "";
        String mac_address = "";
        String line_ending = "";
        String field_delim = "";
        String field_names = "";
        String field_values = "";

        try {
            // Get HTTP Post Request parameters and strip away "\"".
            location = routingContext.request().getParam("location").replaceAll("\"", "");
            reader_name = routingContext.request().getParam("reader_name").replaceAll("\"", "");
            mac_address = routingContext.request().getParam("mac_address").replaceAll("\"", "");
            line_ending = routingContext.request().getParam("line_ending").replaceAll("\"", "");
            field_delim = routingContext.request().getParam("field_delim").replaceAll("\"", "");
            field_names = routingContext.request().getParam("field_names").replaceAll("\"", "");
            field_values = routingContext.request().getParam("field_values").replaceAll("\"", "");
        } catch (Exception e) {
            String JsonBody = routingContext.getBodyAsString();
            logger.error("Missing Parameter: " + e.getMessage());
            logger.debug("Request Body: " + JsonBody);
        }

        HttpServerResponse response = routingContext.response();
        if (reader_name == null ||
                mac_address == null ||
                line_ending == null ||
                field_delim == null ||
                field_names == null ||
                field_values == null) {
            sendError(400, response);
            logger.debug("reader_name is null");
        } else {
            logger.trace("reader_name is " + reader_name);
            logger.trace("mac_address is " + mac_address);
            logger.trace("line_ending is " + line_ending);
            logger.trace("field_delim is " + field_delim);
            logger.trace("field_names is " + field_names);
            logger.trace("field_values is " + field_values);

            /*
            1. Look-up reader for its location
               antenna_port,epc,first_seen_timestamp,peak_rssi
               2,"2016090208473A0110500065",1502871327227745,-61
               ...
            2. Re-format it in JSON and put it on the message event bus.
               {
                   "location" : "DC1",
                   "reader_name" : "709DemoExit1",
                   "mac_address" : "00:16:25:11:CB:03",
                   "inventoryUpdateArray" : [ {
                     "antenna_port" : "3",
                     "epc" : "2016090208473A0110500091",
                     "first_seen_timestamp" : "1503632155035362",
                     "peak_rssi" : "-59"
                   }, {
                     "antenna_port" : "3",
                     "epc" : "20130422848303000102004C",
                     "first_seen_timestamp" : "1503632159334366",
                     "peak_rssi" : "-70"
                   } ]
                 }
             */

            JsonObject inventoryUpdates = new JsonObject();
            inventoryUpdates.put("location", location);
            inventoryUpdates.put("reader_name", reader_name);
            inventoryUpdates.put("mac_address", mac_address);
            inventoryUpdates.put("reader_type", "FIXED");

            String[] fnames = field_names.split(",");
            String[] lines = field_values.split(line_ending);
            int numLine = 0;

            JsonArray inventoryUpdateArray = new JsonArray();

            // Hash Inventory inventoryUpdateArray and keeping the earliest copy.
            HashMap <String, JsonObject> inventoryUpdateMap = new HashMap<String, JsonObject>();

            for (String line: lines) {
                String[] fvalues = line.split(field_delim);

                JsonObject inventoryUpdate = new JsonObject();
                for (int i=0; i < fnames.length; i++) {
                    inventoryUpdate.put(fnames[i], fvalues[i]);
                }

                String thisEPC = inventoryUpdate.getString("epc");
                //Is this EPC new?
                if(!inventoryUpdateMap.containsKey(thisEPC)) {
                    inventoryUpdateMap.put(thisEPC, inventoryUpdate);
                    inventoryUpdateArray.add(inventoryUpdate);
                }

                numLine++;
            }

            logger.info("HTTP Post: Received " + numLine + " record(s)");

            // Once going through all the updates, put the update JsonArray into the update JsonObject for Eventbus.
            inventoryUpdates.put("updates", inventoryUpdateArray);
            logger.debug(inventoryUpdates.encode());

            // Sends the inventory inventoryUpdateArray on the event bus.
            vertx.eventBus().send("Inventory", inventoryUpdates, ar -> {
                if (ar.succeeded()) {
                    logger.info("Inventory Eventbus: Sent " + inventoryUpdates.getJsonArray("updates").size() + " record(s)");
                    logger.info("Inventory Received reply: " + ar.result().body());
                } else {
                    logger.error("Unable to send to Inventory Eventbus: " + ar.cause().getMessage());
                }
            });


            //May not need to add additional response handling since it is not targeted for end-user.
            response.setStatusCode(200).end();
        }
    }

    private void SWCAlertHandler(RoutingContext routingContext) {
        String aLocation = "";
        String reader = "";
        String epc = "";
        String name = "";

        try {
            // Get HTTP Post Request parameters and strip away "\"".
            aLocation = routingContext.request().getParam("Assigned Location").replaceAll("\"", "");
            reader = routingContext.request().getParam("Fixed Reader ID").replaceAll("\"", "");
            epc = routingContext.request().getParam("EPC").replaceAll("\"", "");
            name = routingContext.request().getParam("Name").replaceAll("\"", "");
        } catch (Exception e) {
            String JsonBody = routingContext.getBodyAsString("UTF-16");
            logger.error("Missing Parameter: " + e.getMessage());
            logger.debug("Request Body: " + JsonBody);
        }

        HttpServerResponse response = routingContext.response();
        if (aLocation == null) {
            sendError(400, response);
            logger.debug("location is null");
        } else {
            //Trigger Alarm
            String hostname = ReaderAlarmIPHashMap.get(reader);
            logger.info("HTTP Post: Alert - Asset " + name +
                    " EPC " + epc +
                    " assigned location is " + aLocation +
                    " detected by " + reader +
                    " trigger alarm at " + hostname +
                    " for " + AlarmDurationIn100ms / 10 + " sec.");

//            String hostnameWithAuthen = hostname;
//            if (!config().getString("alarm.user").isEmpty()) {
//                hostnameWithAuthen = config().getString("alarm.user") + ":" +
//                        config().getString("alarm.password") +
//                        "@" + hostname;
//            }
//            client.getNow(config().getInteger("alarm.port", 80), hostnameWithAuthen, "/rc.cgi?o=1," + AlarmDurationIn100ms, res -> {
////                logger.info("Turn on alarm at " + hostnameWithAuthen + " ("+ res.statusCode() + ").");
//                logger.info("Turn on alarm ("+ res.statusCode() + ").");
//            });

            if (hostname == null) {
                logger.error("Alarm Box IP is null. Please check READER_NAME & ALARM_IP is set correctly in CS.");
            } else {
                client
                        .get(config().getInteger("alarm.port", 80), hostname, "/rc.cgi?o=1," + AlarmDurationIn100ms)
                        .putHeader(HttpHeaders.Names.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()))
                        .send(ar -> {
                            if (ar.succeeded()) {
                                logger.info("Turn on alarm with status code (" + ar.result().statusCode() + ")");
                            } else {
                                logger.info("Failed to trigger alarm: Status Code: " + ar.result().statusCode() + ", Msg: " + ar.cause().getMessage());
                            }
                        });
            }
            //Alway going to return 200 to Impinj Reader.
            response.setStatusCode(200).end();
        }
    }

    private void TSLHandler(RoutingContext routingContext) {

        /*
        HTTP Method: PUT
        URL: http://<ip-address>:<port>/api/v1/reader/TSL/<Location>
        BODY (application/json):

        {
            "reader_name" : "TSLDC1Reader1",
            "user_name" : "roychow",
            "session_timestamp" : 1506981439,
            "session_updates" :
                [{
                    "EPC" : "20130422848303000102004C",
                    "guid" : "NETWORK_EQUIPMENT_DC1_EI120816_01",
                    "$aDetectedLocation" : "DC1_NW-G10",
                    "LAST_INVENTORY_TAKE_TIME" : 1505981438,
                    "type" : "NETWORK_EQUIPMENT"
                }, {
                    "EPC" : "2016090208473A0110500091",
                    "guid" : "NETWORK_EQUIPMENT_DC1_DC110DC6A_22",
                    "$aDetectedLocation" : "DC1_NW-G10",
                    "LAST_INVENTORY_TAKE_TIME" : 1505981438,
                    "type" : "NETWORK_EQUIPMENT"
                }]
        }
        */

        // Get HTTP Post Request parameters and strip away "\"".
        String session_location = routingContext.request().getParam("location").replaceAll("\"", "");

        // Get Json from Body
        JsonObject body = new JsonObject();
        String reader_name = null;
        String user_name = null;
        long session_timestamp = 0;
        JsonArray inventoryUpdateArray = new JsonArray();

        try {
            body = routingContext.getBodyAsJson();
            reader_name  = body.getString("reader_name").replaceAll("\"", "");
            user_name    = body.getString("user_name").replaceAll("\"", "");
            session_timestamp    = body.getLong("session_timestamp");
            inventoryUpdateArray = body.getJsonArray("session_updates");
            logger.info("HTTP Post: Received " + inventoryUpdateArray.size() + " record(s)");

            // Once going through all the updates, put the update JsonArray into the update JsonObject for Eventbus.
            JsonObject inventoryUpdates = new JsonObject();
            inventoryUpdates.put("location", session_location);
            inventoryUpdates.put("reader_name", reader_name);
            inventoryUpdates.put("session_timestamp", session_timestamp);
            inventoryUpdates.put("reader_type", "MOBILE");
            inventoryUpdates.put("user_name", user_name);
            inventoryUpdates.put("updates", inventoryUpdateArray);

            logger.debug(inventoryUpdates.encode());

            // Sends the inventory inventoryUpdateArray on the event bus.
            vertx.eventBus().send("Inventory", inventoryUpdates, ar -> {
                if (ar.succeeded()) {
                    logger.info("Inventory Eventbus: Sent " + inventoryUpdates.getJsonArray("updates").size() + " record(s)");
                    logger.info("Inventory Received reply: " + ar.result().body());
                } else {
                    logger.error("Unable to send to Inventory Eventbus: " + ar.cause().getMessage());
                }
            });

            // Response with NumOfUpdates
            JsonObject resultJson = new JsonObject();
            resultJson.put("NumOfUpdates", inventoryUpdateArray.size());


            routingContext.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(resultJson.encodePrettily());

        } catch (Exception e) {
            logger.error("Error in HTTP Put. Exception : " + e);
            sendError(400, routingContext.response());

            String JsonBody = routingContext.getBodyAsString("UTF-8");
            logger.error("Missing Parameter: " + e.getMessage());
            logger.debug("Request Body: " + JsonBody);
        }
    }

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }

    //<Reader_Name, AlarmDevice_IP>
    private HashMap<String, String> getReaderAlarmIPHashMap(JsonArray map) {
        HashMap<String, String> ReaderAlarmIPHashMap = new HashMap<String, String>();
        for (int i = 0; i < map.size(); i++) {
            JsonObject mapItem = map.getJsonObject(i);
            if (mapItem.containsKey("READER_NAME")) {
                if (!mapItem.getString("READER_NAME").isEmpty()) {
                    ReaderAlarmIPHashMap.put(mapItem.getString("READER_NAME"), mapItem.getString("ALARM_IP"));
                }
            }
        }
        return ReaderAlarmIPHashMap;
    }

    @Override
    public void stop() {
        vertx.close();
    }
}