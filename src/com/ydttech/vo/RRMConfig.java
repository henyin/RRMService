package com.ydttech.vo;

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

    String invokeType;
    String entryDI;
    String entryPort;

    String entry1DO;
    String entry2DO;
    String entry1Port;
    String entry2Port;

    String ioCtrlId;
    String ioCtrlIp;

    public String getIoCtrlId() {
        return ioCtrlId;
    }

    public void setIoCtrlId(String ioCtrlId) {
        this.ioCtrlId = ioCtrlId;
    }

    public String getIoCtrlIp() {
        return ioCtrlIp;
    }

    public void setIoCtrlIp(String ioCtrlIp) {
        this.ioCtrlIp = ioCtrlIp;
    }

    public String getEntryDI() {
        return entryDI;
    }

    public void setEntryDI(String entryDI) {
        this.entryDI = entryDI;
    }

    public String getEntryPort() {
        return entryPort;
    }

    public void setEntryPort(String entryPort) {
        this.entryPort = entryPort;
    }

    public String getEntry1DO() {
        return entry1DO;
    }

    public void setEntry1DO(String entry1DO) {
        this.entry1DO = entry1DO;
    }

    public String getEntry2DO() {
        return entry2DO;
    }

    public void setEntry2DO(String entry2DO) {
        this.entry2DO = entry2DO;
    }

    public String getEntry1Port() {
        return entry1Port;
    }

    public void setEntry1Port(String entry1Port) {
        this.entry1Port = entry1Port;
    }

    public String getEntry2Port() {
        return entry2Port;
    }

    public void setEntry2Port(String entry2Port) {
        this.entry2Port = entry2Port;
    }

    public String getInvokeType() {
        return invokeType;
    }

    public void setInvokeType(String invokeType) {
        this.invokeType = invokeType;
    }

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
                ", invokeType='" + invokeType + '\'' +
                ", entryDI='" + entryDI + '\'' +
                ", entryPort='" + entryPort + '\'' +
                ", entry1DO='" + entry1DO + '\'' +
                ", entry2DO='" + entry2DO + '\'' +
                ", entry1Port='" + entry1Port + '\'' +
                ", entry2Port='" + entry2Port + '\'' +
                ", ioCtrlId='" + ioCtrlId + '\'' +
                ", ioCtrlIp='" + ioCtrlIp + '\'' +
                ", tagPatternList=" + tagPatternList +
                '}';
    }
}
