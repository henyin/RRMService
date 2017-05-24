package com.ydttech;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Ean.Chung on 2016/10/4.
 */
public class RRMConfig {

    String readerName;
    String ipAddr;
    Map<String, String> power;

    String arriveURL;
    String departureURL;
    String alarmURL;

    String dbURL;
    String purgeDay;

    String minTemperature, maxTemperature;
    String connBrokenTimeoutLimit, connBrokenTimesLimit;

    String departureTimeout;

    private List tagPatternList = new ArrayList<String>();

    private final String DEPARTURE_TIMEOUT_MILLISECONDS = "1000";

    public RRMConfig() {
    }

    public String getReaderName() {
        return readerName;
    }

    public void setReaderName(String readerName) {
        this.readerName = readerName;
    }

    public String getIpAddr() {
        return ipAddr;
    }

    public void setIpAddr(String ipAddr) {
        this.ipAddr = ipAddr;
    }

    public Map<String, String> getPower() {
        return power;
    }

    public void setPower(Map<String, String> power) {
        this.power = power;
    }

    public String getArriveURL() {
        return arriveURL;
    }

    public void setArriveURL(String arriveURL) {
        this.arriveURL = arriveURL;
    }

    public String getDepartureURL() {
        return departureURL;
    }

    public void setDepartureURL(String departureURL) {
        this.departureURL = departureURL;
    }

    public String getAlarmURL() {
        return alarmURL;
    }

    public void setAlarmURL(String alarmURL) {
        this.alarmURL = alarmURL;
    }

    public String getDbURL() {
        return dbURL;
    }

    public void setDbURL(String dbURL) {
        this.dbURL = dbURL;
    }

    public String getMinTemperature() {
        return minTemperature;
    }

    public void setMinTemperature(String minTemperature) {
        this.minTemperature = minTemperature;
    }

    public String getMaxTemperature() {
        return maxTemperature;
    }

    public void setMaxTemperature(String maxTemperatue) {
        this.maxTemperature = maxTemperatue;
    }

    public String getConnBrokenTimeoutLimit() {
        return connBrokenTimeoutLimit;
    }

    public void setConnBrokenTimeoutLimit(String connBrokenTimeout) {
        this.connBrokenTimeoutLimit = connBrokenTimeout;
    }

    public String getConnBrokenTimesLimit() {
        return connBrokenTimesLimit;
    }

    public void setConnBrokenTimesLimit(String connBrokenTimesLimit) {
        this.connBrokenTimesLimit = connBrokenTimesLimit;
    }

    public String getDepartureTimeout() {
        return departureTimeout;
    }

    public void setDepartureTimeout(String departureTimeout) {
        if (departureTimeout.isEmpty())
            this.departureTimeout = DEPARTURE_TIMEOUT_MILLISECONDS;
        else
            this.departureTimeout = departureTimeout;
    }

    public String getPurgeDay() {
        return purgeDay;
    }

    public void setPurgeDay(String purgeDay) {
        this.purgeDay = purgeDay;
    }

    public List getTagPatternList() {
        return tagPatternList;
    }

    public void setTagPatternList(List tagPatternList) {
        this.tagPatternList = tagPatternList;
    }

    @Override
    public String toString() {
        return "RRMConfig{" +
                "readerName='" + readerName + '\'' +
                ", ipAddr='" + ipAddr + '\'' +
                ", power=" + power +
                ", arriveURL='" + arriveURL + '\'' +
                ", departureURL='" + departureURL + '\'' +
                ", alarmURL='" + alarmURL + '\'' +
                ", dbURL='" + dbURL + '\'' +
                ", purgeDay='" + purgeDay + '\'' +
                ", minTemperature='" + minTemperature + '\'' +
                ", maxTemperature='" + maxTemperature + '\'' +
                ", connBrokenTimeoutLimit='" + connBrokenTimeoutLimit + '\'' +
                ", connBrokenTimesLimit='" + connBrokenTimesLimit + '\'' +
                ", departureTimeout='" + departureTimeout + '\'' +
                ", tagPatternList=" + tagPatternList +
                '}';
    }
}
