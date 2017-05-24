package com.ydttech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Thread.sleep;
import static java.lang.Thread.yield;

/**
 * Created by Ean.Chung on 2016/10/13.
 */
public class EventDataAnalyst implements Runnable {

    private static Logger logger = LoggerFactory.getLogger("EventDataAnalyst");

    private int DEPARTURE_TIMEOUT_MILLISECONDS = 1000;

    private Map<String, NormalEventData> currDataMap = null; //new ConcurrentHashMap<String, NormalEventData>();

    private RRMConfig rrmConfig;

    public boolean keepRunning = true;

    private LogDb logDb;

    private NormalEventData normalEventData = null;

    SimpleDateFormat dstSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    String fmt_time;


    public EventDataAnalyst(RRMConfig rrmConfig, ConcurrentHashMap<String, NormalEventData> curHashMap) {
        this.rrmConfig = rrmConfig;
        this.currDataMap = curHashMap;
        logDb = new LogDb(rrmConfig.getDbURL());
        logDb.init();
    }

    public void run() {

//        NormalEventData normalEventData = null;

        DEPARTURE_TIMEOUT_MILLISECONDS = Integer.parseInt(rrmConfig.getDepartureTimeout());
        synchronized (currDataMap) {
        if (!currDataMap.isEmpty()) {

                Iterator<NormalEventData> listIterator = currDataMap.values().iterator();

                while (listIterator.hasNext()) {

                    long currTimeMillis = System.currentTimeMillis();
                    try {
                        normalEventData = (NormalEventData) listIterator.next().clone();
                    } catch (CloneNotSupportedException e) {
                        e.printStackTrace();
                    }

                    if ((normalEventData.getEvent_name().equalsIgnoreCase(EventName.REPORT) ||
                            normalEventData.getEvent_name().equalsIgnoreCase(EventName.ARRIVE))) {

                        if (currTimeMillis - normalEventData.getpLatestTimeMillis() >= DEPARTURE_TIMEOUT_MILLISECONDS) {
                            currDataMap.remove(normalEventData.getEpc());

                            fmt_time = dstSdf.format(new Date());

                            normalEventData.setTime(fmt_time);
                            normalEventData.setEvent_name(EventName.DEPARTURE);
                            normalEventData.setpLatestTimeMillis(System.currentTimeMillis());

                            Thread postDepart = new Thread(new PostNormalData(rrmConfig.getDepartureURL(), normalEventData));
                            postDepart.setPriority(Thread.MAX_PRIORITY);
                            postDepart.start();

                            logger.info("Reader:{} epc:{} event_name:{} time:{} timeout={}",
                                    normalEventData.getDevice_name(), normalEventData.getEpc(), normalEventData.getEvent_name(), normalEventData.getTime(),
                                    DEPARTURE_TIMEOUT_MILLISECONDS);

                            logDb.addNormalEvent(normalEventData);
                        }
                    }
                    yield();
                }
            }
            currDataMap.notify();
        }
        System.gc();
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
