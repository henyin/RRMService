package com.ydttech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Thread.yield;

/**
 * Created by Ean.Chung on 2016/10/13.
 */
public class EventProcess extends Thread {

    private static Logger logger = LoggerFactory.getLogger("EventProcess");

    private int DEPARTURE_TIMEOUT_MILLISECONDS = 1000;

    private Map<String, NormalEventData> currDataMap = null; //new ConcurrentHashMap<String, NormalEventData>();

    private RRMConfig rrmConfig;

    public boolean keepRunning = true;

    private LogDb logDb;

    private NormalEventData normalEventData = null;

    SimpleDateFormat dstSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    String fmt_time;


    public EventProcess(RRMConfig rrmConfig, NormalEventData normalEventData) {
        this.rrmConfig = rrmConfig;
        this.normalEventData = normalEventData;
        logDb = new LogDb(rrmConfig.getDbURL());
        logDb.init();
    }

    public void run() {

//        NormalEventData normalEventData = null;

        DEPARTURE_TIMEOUT_MILLISECONDS = Integer.parseInt(rrmConfig.getDepartureTimeout());
        synchronized (normalEventData) {
            while (!normalEventData.getEvent_name().equalsIgnoreCase(EventName.DEPARTURE)) {

                    long currTimeMillis = System.currentTimeMillis();

                    if ((normalEventData.getEvent_name().equalsIgnoreCase(EventName.REPORT) ||
                            normalEventData.getEvent_name().equalsIgnoreCase(EventName.ARRIVE))) {

                        if (currTimeMillis - normalEventData.getpLatestTimeMillis() >= DEPARTURE_TIMEOUT_MILLISECONDS) {
//                            currDataMap.remove(normalEventData.getEpc());

                            fmt_time = dstSdf.format(new Date());

                            normalEventData.setTime(fmt_time);
                            normalEventData.setEvent_name(EventName.DEPARTURE);
                            normalEventData.setpLatestTimeMillis(System.currentTimeMillis());

                            new Thread(new PostNormalData(rrmConfig.getDepartureURL(), normalEventData)).start();
                            logger.info("Reader:{} epc:{} event_name:{} time:{} timeout={}",
                                    normalEventData.getDevice_name(), normalEventData.getEpc(), normalEventData.getEvent_name(), normalEventData.getTime(),
                                    DEPARTURE_TIMEOUT_MILLISECONDS);

                            logDb.addNormalEvent(normalEventData);
                        }
                    }
                try {
                    sleep(300000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                yield();
            }
        }
        currDataMap.notify();
        System.gc();
    }

    public void add(NormalEventData packedEventData) {

        String tagKey = packedEventData.getEpc();
//        NormalEventData reqPackedEventData = null;

        synchronized (normalEventData) {
            if (packedEventData.getTid() != null) {
                packedEventData.setEvent_name(EventName.ARRIVE);
                try {
//                    normalEventData = (NormalEventData) packedEventData.clone();
                    normalEventData = packedEventData;
                    new Thread(new PostNormalData(rrmConfig.getArriveURL(), normalEventData)).start();
                    logger.info("arrive:{}", normalEventData.getEvent_name());
                } catch (Exception e) {
                    StringWriter error = new StringWriter();
                    e.printStackTrace(new PrintWriter(error));
                    logger.error(error.toString());
                }
            } else if (normalEventData.getEvent_name().equals(EventName.ARRIVE)) {
                normalEventData = packedEventData;
                normalEventData.setEvent_name(packedEventData.getEvent_name());
                logger.info("report:{}", normalEventData.getEvent_name());
            } else if (normalEventData.getEvent_name().equals(EventName.REPORT)) {
                normalEventData = packedEventData;
                normalEventData.setEvent_name(packedEventData.getEvent_name());
            } else {
                logger.info("Unexpected Reader:{} epc:{} event_name:{}",
                        packedEventData.getDevice_name(), packedEventData.getEpc(), currDataMap.get(tagKey).getEvent_name());
            }

            logger.info("Reader:{} epc:{} event_name:{} time:{}",
                    normalEventData.getDevice_name(), normalEventData.getEpc(), normalEventData.getEvent_name(), normalEventData.getTime());

            logDb.addNormalEvent(normalEventData);
            tagKey = null;
        }

    }

    public void put(NormalEventData packedEventData) {

//        synchronized (packedEventData) {
            String tagKey = packedEventData.getEpc();

        if (packedEventData.getEvent_name().equalsIgnoreCase(EventName.DEPARTURE)) {
                try {
                    logger.info("Reader:{} epc:{} departure:{}",
                            packedEventData.getDevice_name(), packedEventData.getEpc(), packedEventData.getTime());
//                    new Thread(new PostNormalData(rrmConfig.getDepartureURL(), packedEventData)).start();
                    currDataMap.remove(packedEventData.getEpc());
                } catch (Exception e) {
                    StringWriter error = new StringWriter();
                    e.printStackTrace(new PrintWriter(error));
                    logger.error(error.toString());
                }
            } else {
                logger.info("Unexpected Reader:{} epc:{} event_name:{}",
                        packedEventData.getDevice_name(), packedEventData.getEpc(), currDataMap.get(tagKey).getEvent_name());
            }

            logDb.addNormalEvent(packedEventData);

//        }
    }

}
