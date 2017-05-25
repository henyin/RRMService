package com.ydttech.vo;

/**
 * Created by Ean.Chung on 2016/10/17.
 */
public class AlarmEventData {

    private String event_name;
    private String device_name;
    private String alarm_code;
    private String alarm_reason;
    private String time;
    private long pLatestTimeMillis;

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

    public String getAlarm_code() {
        return alarm_code;
    }

    public void setAlarm_code(String alarm_code) {
        this.alarm_code = alarm_code;
    }

    public String getAlarm_reason() {
        return alarm_reason;
    }

    public void setAlarm_reason(String alarm_reason) {
        this.alarm_reason = alarm_reason;
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
}
