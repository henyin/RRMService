package com.ydttech.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ean.Chung on 2016/10/13.
 */
public class NormalEventData implements Cloneable {

    private String event_name;
    private String device_name;
    private String epc;
    private String tid;
    private String antenna;
    private String rssi;
    private String time;
    private long pLatestTimeMillis;
    private final String LTagPattern = "09300402";
    private final String MTagPattern = "20160107";
    private boolean isVerified = false;
    private int readCount = 0;

    public int getReadCount() {
        return readCount;
    }

    public void setReadCount(int readCount) {
        this.readCount = readCount;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void setVerified(boolean verified) {
        isVerified = verified;
    }

    public String getLTagPattern() {
        return LTagPattern;
    }

    public String getMTagPattern() {
        return MTagPattern;
    }

    public String getEvent_name() {
        return event_name;
    }

    public void setEvent_name(String event_name) {
        this.event_name = event_name;
    }

    public String getDevice_name() {
        return device_name;
    }

    public void setDevice_name(String device_name) {
        this.device_name = device_name;
    }

    public String getEpc() {
        return epc;
    }

    public void setEpc(String ecp) {
        this.epc = ecp;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public String getAntenna() {
        return antenna;
    }

    public void setAntenna(String antenna) {
        this.antenna = antenna;
    }

    public String getRssi() {
        return rssi;
    }

    public void setRssi(String rssi) {
        this.rssi = rssi;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public long getpLatestTimeMillis() {
        return pLatestTimeMillis;
    }

    public void setpLatestTimeMillis(long pLatestTimeMillis) {
        this.pLatestTimeMillis = pLatestTimeMillis;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
