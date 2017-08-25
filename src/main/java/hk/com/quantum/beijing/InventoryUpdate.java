package hk.com.quantum.beijing;

import java.sql.Timestamp;

/**
 * Created by Ricardo on 8/16/2017.
 * 2,"2016090208473A0110500065",1502871327227745,-61
 */
public class InventoryUpdate {
    private int antenna;
    private String epc;
    private String timestamp;
    private int rssi;

    public int getAntenna() {
        return antenna;
    }

    public void setAntenna(int antenna) {
        this.antenna = antenna;
    }

    public String getEpc() {
        return epc;
    }

    public void setEpc(String epc) {
        this.epc = epc;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public int getRssi() {
        return rssi;
    }

    public void setRssi(int rssi) {
        this.rssi = rssi;
    }
}
