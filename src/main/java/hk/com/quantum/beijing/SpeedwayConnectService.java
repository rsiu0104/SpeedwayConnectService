package hk.com.quantum.beijing;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.HashMap;

public class SpeedwayConnectService extends AbstractVerticle {

    //Logging
    private static final Logger logger = LoggerFactory.getLogger(SpeedwayConnectService.class.getName());

    @Override
    public void start() throws Exception {

        // API Endpoint that receives HTTP Post from Impinj Readers
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/api/v1/reader/SpeedwayConnect/:location").handler(this::SWCHandler);
        router.put("/api/v1/reader/TSL/:location").handler(this::TSLHandler);
        vertx.createHttpServer().requestHandler(router::accept).listen(config().getInteger("http.port", 8083));
    }

    private void SWCHandler(RoutingContext routingContext) {

        // Get HTTP Post Request parameters and strip away "\"".
        String location     = routingContext.request().getParam("location").replaceAll("\"", "");
        String reader_name  = routingContext.request().getParam("reader_name").replaceAll("\"", "");
        String mac_address  = routingContext.request().getParam("mac_address").replaceAll("\"", "");
        String line_ending  = routingContext.request().getParam("line_ending").replaceAll("\"", "");
        String field_delim  = routingContext.request().getParam("field_delim").replaceAll("\"", "");
        String field_names  = routingContext.request().getParam("field_names").replaceAll("\"", "");
        String field_values = routingContext.request().getParam("field_values").replaceAll("\"", "");

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
            logger.debug("reader_name is " + reader_name);
            logger.debug("mac_address is " + mac_address);
            logger.debug("line_ending is " + line_ending);
            logger.debug("field_delim is " + field_delim);
            logger.debug("field_names is " + field_names);
            logger.debug("field_values is " + field_values);

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

            logger.debug(inventoryUpdates.encodePrettily());

            // Sends the inventory inventoryUpdateArray on the event bus.
            vertx.eventBus().send("eb", inventoryUpdates, ar -> {
                if (ar.succeeded()) {
                    logger.info("Eventbus: Sent " + inventoryUpdates.getJsonArray("updates").size() + " record(s)");
                    logger.info("Received reply: " + ar.result().body());
                } else {
                    logger.error("Unable to send to Eventbus: " + ar.cause().getMessage());
                }
            });


            //May not need to add additional response handling since it is not targeted for end-user.
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
        int session_timestamp = 0;
        JsonArray inventoryUpdateArray = new JsonArray();

        try {
            body = routingContext.getBodyAsJson();
            reader_name  = body.getString("reader_name").replaceAll("\"", "");
            user_name    = body.getString("user_name").replaceAll("\"", "");
            session_timestamp    = body.getInteger("session_timestamp");
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

            logger.debug(inventoryUpdates.encodePrettily());

            // Sends the inventory inventoryUpdateArray on the event bus.
            vertx.eventBus().send("eb", inventoryUpdates, ar -> {
                if (ar.succeeded()) {
                    logger.info("Eventbus: Sent " + inventoryUpdates.getJsonArray("updates").size() + " record(s)");
                    logger.info("Received reply: " + ar.result().body());
                } else {
                    logger.error("Unable to send to Eventbus: " + ar.cause().getMessage());
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
        }
    }

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }

    @Override
    public void stop() {
        vertx.close();
    }
}