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

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

public class SpeedwayConnectService extends AbstractVerticle {

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");

    //Logging
    private static final Logger logger = LoggerFactory.getLogger(SpeedwayConnectService.class.getName());

    @Override
    public void start() throws Exception {



        // API Endpoint that receives HTTP Post from Impinj Readers
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/api/v1/reader/SpeedwayConnect/:location").handler(this::handlePost);
        vertx.createHttpServer().requestHandler(router::accept).listen(config().getInteger("http.port", 8083));

        // Sends the inventory updates on the event bus.
        //vertx.eventBus().publish("InventoryUpdates", toJson());
    }

    private void handlePost(RoutingContext routingContext) {

        // Get HTTP Post Request parameters and strip away "\"".
        String location     = routingContext.request().getParam("location").replaceAll("\"", "");
        String reader_name  = routingContext.request().getParam("reader_name").replaceAll("\"", "");
        String mac_address  = routingContext.request().getParam("mac_address").replaceAll("\"", "");
        String line_ending  = routingContext.request().getParam("line_ending").replaceAll("\"", "");
        String field_delim  = routingContext.request().getParam("field_delim").replaceAll("\"", "");
        String field_names  = routingContext.request().getParam("field_names").replaceAll("\"", "");
        String field_values = routingContext.request().getParam("field_values").replaceAll("\"", "");

        HttpServerResponse response = routingContext.response();
        if (location == null) {
            sendError(400, response);
            logger.debug("location is null");
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
                   "updates" : [ {
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

            String[] fnames = field_names.split(",");
            String[] lines = field_values.split(line_ending);

            JsonArray updates = new JsonArray();

            for (String line: lines) {
                String[] fvalues = line.split(field_delim);
                JsonObject inventoryUpdate = new JsonObject();

                for (int i=0; i < fnames.length; i++) {
                    inventoryUpdate.put(fnames[i], fvalues[i]);
                }

                updates.add(inventoryUpdate);
            }

            inventoryUpdates.put("updates", updates);

            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            logger.debug(sdf.format(timestamp) + ": \n" + inventoryUpdates.encodePrettily());

            //May not need to add additional response handling since it is not targeted for end-user.
            response.setStatusCode(200).end();
        }
    }

    private void sendError(int statusCode, HttpServerResponse response) {
        response.setStatusCode(statusCode).end();
    }
}