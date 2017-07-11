package com.ydttech.core;

import com.ydt.driver.ConnectionException;
import com.ydt.driver.ConnectionType;
import com.ydt.driver.IEventDataListener;
import com.ydt.invoke.InvokeError;
import com.ydt.log.Report;
import com.ydt.reader.Command;
import com.ydt.reader.ReaderClient;
import com.ydt.types.EventData;
import com.ydt.types.EventType;
import com.ydttech.util.LogDb;
import com.ydttech.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;
import static java.lang.Thread.yield;

/**
 * Created by Ean.Chung on 2016/10/12.
 */
public class ReaderDev implements Runnable {

    private Logger logger = LoggerFactory.getLogger("ReaderDev");

    private  EventDataAnalyst eventDataAnalyst;
    Thread doAnalyst;

    private ReaderClient readerClient;

    private int minTemperature = -30;
    private int maxTemperature = 60;
    private boolean connected = false;
    private RRMConfig rrmConfig;
    private String deviceName;
    private String alarmURL;
    private int connBrokenTimesLimit = 3;
    private int connBrokenTimeoutLimit = 10000;
    private int currConnBrokenTimes = 0;
    private boolean rebootFlag = false;
    private final int REBOOT_WAITING_TIME = 60000;
    private long lastConnectedTime;
    private List<String> tagPatternList;

    private LogDb logDb;

    private EventObj eventObj = new EventObj();
    private SelfCheckJob selfCheckJob = new SelfCheckJob();
    private ScheduledExecutorService analystScheduled = Executors.newSingleThreadScheduledExecutor();
    private Map<String, NormalEventData> currDataMap = new ConcurrentHashMap<String, NormalEventData>();

    private NormalEventData reqPackedEventData = null;

    private SimpleDateFormat dstSdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private String alarm_fmt_time, event_fmt_time;
    private int currDayOfYear  = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);

    public ReaderDev(RRMConfig rrmConfig) {
        this.rrmConfig = rrmConfig;
        deviceName = rrmConfig.getReaderName();
        alarmURL = rrmConfig.getAlarmURL();
        minTemperature = Integer.parseInt(rrmConfig.getMinTemperature());
        maxTemperature = Integer.parseInt(rrmConfig.getMaxTemperature());
        connBrokenTimesLimit = Integer.parseInt(rrmConfig.getConnBrokenTimesLimit());
        connBrokenTimeoutLimit = Integer.parseInt(rrmConfig.getConnBrokenTimeoutLimit());
        tagPatternList = new ArrayList<String>(rrmConfig.getTagPatternList());

        logger = LoggerFactory.getLogger(rrmConfig.getInvokeType()+"-"+deviceName);

        logDb = new LogDb(rrmConfig.getDbURL() + rrmConfig.getReaderName() + ".db");
        logDb.init();

        eventDataAnalyst = new EventDataAnalyst(rrmConfig, (ConcurrentHashMap) currDataMap);
        analystScheduled.scheduleAtFixedRate(eventDataAnalyst, 0, Integer.parseInt(rrmConfig.getDepartureTimeout())/2, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                logger.info("reader:{} device standby!", deviceName);
                standby();
            }
        });

        Timer timer = new Timer();
        long delayTime = 60000;
        timer.schedule(selfCheckJob, delayTime, getConnBrokenTimeoutLimit());

        try {
            readerClient = new ReaderClient(ConnectionType.SOCKET, rrmConfig.getIpAddr());
        } catch (ConnectionException e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            logger.error("reader:{} msg:{}", rrmConfig.getReaderName(), error.toString());
        }

        readerClient.setReport(getDeviceName(), "../logs/sdk", Report.Level.EVENT, Report.Level.ERROR);

    }

    public int connect(int timeout) {

        int retCode = InvokeError.OK;

        try {
            if (readerClient != null) {
                readerClient.close();
//                readerClient = null;
            }

//            readerClient = new ReaderClient(ConnectionType.SOCKET, rrmConfig.getIpAddr());
//
//            readerClient.setReport(getDeviceName(), "../logs", Report.Level.EVENT, Report.Level.ERROR);
            readerClient.open();
            readerClient.setTimeout(timeout);
            lastConnectedTime = System.currentTimeMillis();

        } catch (Exception e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            retCode = InvokeError.FAIL;
            logger.error("reader:{} msg:{}", rrmConfig.getReaderName(), error.toString());
        }

        return retCode;
    }

    public int login() {

        readerClient.setEventListerner(eventObj);


        readerClient.use(Command.USER_LOG_IN)
                .with("account", "admin")
                .with("password", "readeradmin")
                .run();

        return readerClient.getResult().error();
    }

    public int detectAntenna() {

        readerClient.use(Command.ANTENNA_DETECT).run();
        return readerClient.getResult().error();
    }

    public int setAntennaPort() {

        int retCode = InvokeError.OK;
        StringBuilder antennaList = new StringBuilder("");

        for (String id : rrmConfig.getPower().keySet()) {

            if (rrmConfig.getPower().get(id) != "") {

                antennaList.append(id);
                antennaList.append(" ");

                readerClient.use(Command.ANTENNA_SET_POWER)
                        .with("antenna", id)
                        .with("power", rrmConfig.getPower().get(id))
                        .run();

                retCode = readerClient.getResult().error();

                if (retCode == InvokeError.OK) {
                    logger.info("reader:{} Antenna:{} set Power:{} ok!",
                            getDeviceName(),
                            id,
                            rrmConfig.getPower().get(id));
                } else {
                    logger.info("reader:{} Antenna:{} set Power:{} failure!",
                            getDeviceName(),
                            id,
                            rrmConfig.getPower().get(id));
                    break;
                }
            } else {
                logger.info("reader:{} Antenna:{} is disabled!",
                        getDeviceName(),
                        id);
            }
        }

        readerClient.use(Command.ANTENNA_SET_MUX)
                .with("antenna", antennaList.toString())
                .run();

        antennaList.setLength(0);

        return retCode;
    }

    public int setEventDataFmt() {

//        readerClient.setEventListerner(eventObj);

        readerClient.use(Command.READER_EVENT_REG)
                .with(EventType.EVENT_ERROR)
                .run();

        readerClient.use(Command.READER_EVENT_REG)
                .with(EventType.EVENT_TAG_REPORT)
                .run();

        readerClient.use(Command.READER_SET_REPORTFIELD)
                .with("report", "epc tid ant readcnt rssi")
                .run();

        return readerClient.getResult().error();
    }

    public int activity() {

        readerClient.use(Command.READER_ACTIVE).run();

        return readerClient.getResult().error();
    }

    public int standby() {

        readerClient.use(Command.READER_STANDBY).run();

        return readerClient.getResult().error();
    }

    public int getTemperature() {

        readerClient.use(Command.READER_GET_TEMPERATURE).run();

        if (readerClient.getResult().error() == InvokeError.CONN)
            setConnected(false);

        return Integer.parseInt(readerClient.getResult().retString());
    }

    public void stopDevice() {
        connected = false;
        try {
            readerClient.close();
        } catch (ConnectionException e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            logger.error("reader:{} msg:{}", rrmConfig.getReaderName(), error.toString());
        }
    }

    public int getOPMode() {

        int isActive = 0;

        readerClient.use(Command.SETUP_GET_OPMOD).run();

        if (readerClient.getResult().error() == InvokeError.OK) {
            if (readerClient.getResult().retString().equalsIgnoreCase("active"))
                isActive = 1;
            else if (readerClient.getResult().retString().equalsIgnoreCase("standby"))
                isActive = 0;
        } else
            isActive = readerClient.getResult().error();

        return isActive;
    }

    public boolean isAlive() {

        try {
            readerClient.use(Command.READER_ALIVE).run();
        } catch (Exception e) {
            logger.info("exception in isAlive");
            return false;
        }
        return (boolean) readerClient.getResult().retvalue();
    }

    public boolean isRebootFlag() {
        return false;
    }

    public void setRebootFlag(boolean rebootFlag) {
        this.rebootFlag = rebootFlag;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public String getAlarmURL() {
        return alarmURL;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public int getMinTemperature() {
        return minTemperature;
    }

    public int getMaxTemperature() {
        return maxTemperature;
    }

    public int getConnBrokenTimesLimit() {
        return connBrokenTimesLimit;
    }

    public void setConnBrokenTimesLimit(int connBrokenTimesLimit) {
        this.connBrokenTimesLimit = connBrokenTimesLimit;
    }

    public int getConnBrokenTimeoutLimit() {
        return connBrokenTimeoutLimit;
    }

    public void setConnBrokenTimeoutLimit(int connBrokenTimeoutLimit) {
        this.connBrokenTimeoutLimit = connBrokenTimeoutLimit;
    }

    public int getCurrConnBrokenTimes() {
        return currConnBrokenTimes;
    }

    public void setCurrConnBrokenTimes(int currConnBrokenTimes) {
        this.currConnBrokenTimes = currConnBrokenTimes;
    }

    public void addCurrConnBrokenTimes(int BrokenTimes) {
        this.currConnBrokenTimes += BrokenTimes;
    }

    public void reboot() {
//        readerClient.use(Command.READER_REBOOT).run();
        setRebootFlag(false);

    }

    @Override
    public void run() {

        while (true) {
            try {
                if (!isConnected()) {
                    sleep(connBrokenTimeoutLimit);
                    if (connect(connBrokenTimeoutLimit) == InvokeError.OK) {
                        logger.info("reader:{} open connection is OK!", getDeviceName());

                        if (rebootFlag) {
                            logger.info("reader:{} network status may be not stable now, will reset reader network env.!", getDeviceName());
                            setConnected(false);
                            reboot();
//                            sleep(REBOOT_WAITING_TIME);
                            continue;
                        }

                        if (login() == InvokeError.OK) {
                            logger.info("reader:{} login is OK!", getDeviceName());
                            if (setAntennaPort() == InvokeError.OK) {
                                logger.info("reader:{} set antenna port is OK!", getDeviceName());
                                if (standby() == InvokeError.OK) {
                                    if (setEventDataFmt() == InvokeError.OK) {
                                        logger.info("reader:{} set event data format is OK!", getDeviceName());
                                        if (activity() == InvokeError.OK) {
                                            logger.info("reader:{} event channel:{}", getDeviceName(),
                                                    readerClient.getEventChannel().getID());
                                            setConnected(true);

                                            AlarmEventData alarmEventData = new AlarmEventData();

                                            alarm_fmt_time = dstSdf.format(new Date());
                                            alarmEventData.setDevice_name(getDeviceName());
                                            alarmEventData.setEvent_name("alarm");
                                            alarmEventData.setAlarm_code("0");
                                            alarmEventData.setAlarm_reason("Reader:" + getDeviceName() + " initial successfully!");
                                            alarmEventData.setTime(alarm_fmt_time);

                                            logDb.addAlarmEvent(alarmEventData);
                                            logger.info("reader:{} set active is ok!", getDeviceName());

                                        } else
                                            logger.info("reader:{} re-active is failure!", getDeviceName());
                                    } else
                                        logger.info("reader:{} set event data format is failure!", getDeviceName());
                                } else
                                    logger.info("reader:{} set standby is failure!", getDeviceName());
                            } else
                                logger.info("reader:{} set antenna port is failure!", getDeviceName());
                        } else
                            logger.info("reader:{} login is failure!", getDeviceName());
                    } else {
                        logger.info("reader:{} open connection is failure!", getDeviceName());

                        addCurrConnBrokenTimes(1);
                        logger.info("reader:{} re-connect times={}",
                                getDeviceName(),
                                getCurrConnBrokenTimes());

                        if (getCurrConnBrokenTimes() % getConnBrokenTimesLimit() == 0) {

                            logger.info("reader:{} connection is broken {} times in every {} milliseconds!",
                                    getDeviceName(),
                                    getCurrConnBrokenTimes(),
                                    getConnBrokenTimeoutLimit());

                            AlarmEventData alarmEventData = new AlarmEventData();
                            String alarm_fmt_time;

                            alarm_fmt_time = dstSdf.format(new Date());
                            alarmEventData.setDevice_name(getDeviceName());
                            alarmEventData.setEvent_name("alarm");
                            alarmEventData.setAlarm_code("1");
                            alarmEventData.setAlarm_reason("Connection is broken Reader:" + getDeviceName());
                            alarmEventData.setTime(alarm_fmt_time);

                            Thread postThread = new Thread(new PostAlarmData(getAlarmURL(), alarmEventData));
                            postThread.start();

                            logDb.addAlarmEvent(alarmEventData);
                        }
                    }
                } else {
                    System.gc();
                    yield();
                }
            } catch (Exception e) {
                StringWriter error = new StringWriter();
                e.printStackTrace(new PrintWriter(error));
                logger.error("reader:{} is not keep running! e:{}",getDeviceName(), error.toString());
                stopDevice();
            } finally {
                try {
                    System.gc();
                    sleep(connBrokenTimeoutLimit);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    class SelfCheckJob extends TimerTask  {

        private void checkTemperature() {
            int temperature = getTemperature();

            if (temperature >= getMaxTemperature() ||
                    temperature <= getMinTemperature()) {

                AlarmEventData alarmEventData = new AlarmEventData();

                alarm_fmt_time = dstSdf.format(new Date());
                alarmEventData.setDevice_name(getDeviceName());
                alarmEventData.setEvent_name("alarm");
                alarmEventData.setAlarm_code("2");
                alarmEventData.setAlarm_reason("Temperature in warning condition:" + temperature);
                alarmEventData.setTime(alarm_fmt_time);

                Thread postThread = new Thread(new PostAlarmData(getAlarmURL(), alarmEventData));
                postThread.start();

                logDb.addAlarmEvent(alarmEventData);

            }
            logger.info("reader:{} alive:{} temperature:{} ", getDeviceName(), connected, temperature);

        }

        private void checkNetwork() {
            int timeout = 2000;
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces.hasMoreElements()) {
                    NetworkInterface nic = interfaces.nextElement();
                    System.out.print("Interface Name : [" + nic.getDisplayName() + "]");
                    System.out.println(", Is connected : [" + nic.isUp() + "]");
                }
            } catch (Exception e) {
                logger.info("e:{}", e);
            }

        }

        private void purgeLogDB() {
            Calendar cal = Calendar.getInstance();

            if (currDayOfYear != cal.get(Calendar.DAY_OF_YEAR)) {
                currDayOfYear = cal.get(Calendar.DAY_OF_YEAR);
                cal.add(Calendar.DAY_OF_MONTH, 0 - Integer.parseInt(rrmConfig.getPurgeDay()));

                logger.info("DELETE FROM NormalEvent WHERE device_name = '" + rrmConfig.getReaderName() +
                        "' AND time <= '" + dstSdf.format(cal.getTime()) + "'");
                logDb.doSql("DELETE FROM NormalEvent WHERE device_name = '" + rrmConfig.getReaderName() +
                        "' AND time <= '" + dstSdf.format(cal.getTime()) + "'");
            }

        }

        private void getOperatingMode() {

            int opMode = getOPMode();
            if (opMode == 1) {
                logger.info("reader:{} is in active mode!", getDeviceName());
            } else if (opMode == 0) {
                AlarmEventData alarmEventData = new AlarmEventData();

                alarm_fmt_time = dstSdf.format(new Date());
                alarmEventData.setDevice_name(getDeviceName());
                alarmEventData.setEvent_name("alarm");
                alarmEventData.setAlarm_code("3");
                alarmEventData.setAlarm_reason("Reader maybe not in active mode while in connection state!");
                alarmEventData.setTime(alarm_fmt_time);

//                Thread postThread = new Thread(new PostAlarmData(getAlarmURL(), alarmEventData));
//                postThread.start();

                logDb.addAlarmEvent(alarmEventData);

                logger.info("reader:{} is in standby mode while in connection state!", getDeviceName());
                if (activity() == InvokeError.OK) {
                    logger.info("restart reader:{} in active mode successfully, event channel:{}!", getDeviceName(), readerClient.getEventChannel().getID());
                } else {
                    logger.info("restart reader:{} in active mode failure!", getDeviceName());
                }
            } else {
                logger.error("reader:{} get operating mode command is fail! code = {}", getDeviceName(), opMode);
            }
        }

        @Override
        public void run() {

            try {
                if (isConnected()) {
                    if (getCurrConnBrokenTimes() >= 2) {
                        setCurrConnBrokenTimes(0);
                        setRebootFlag(true);
                        logger.info("reader:{} reset network flag:{} next time will be reset!", getDeviceName(), isRebootFlag());
                    }

                    if (currDataMap.isEmpty()) {
                        checkTemperature();
                        purgeLogDB();
                        getOperatingMode();
                        System.gc();
                    } else
                        logger.info("reader:{} routine check process is suspend while tag data is exist! amount of data:{}", getDeviceName(), currDataMap.size());
                } else {
                    logger.info("reader:{} is not connected to RRM!", getDeviceName());
                }
            } catch (Exception e) {
//                stopDevice();
                logger.info("reader:{} routine job is interrupted! msg:{}", getDeviceName(), e.getMessage());
            }
        }
    }

    public void add(NormalEventData packedEventData) {

        String tagKey = packedEventData.getEpc();
//        NormalEventData reqPackedEventData = null;

        synchronized (currDataMap) {

            if (!currDataMap.containsKey(tagKey) && packedEventData.getEpc() != null) {
                packedEventData.setEvent_name(EventName.ARRIVE);
                try {
                    reqPackedEventData = (NormalEventData) packedEventData.clone();
                    currDataMap.put(tagKey, reqPackedEventData);
                    Thread postArrive = new Thread(new PostNormalData(rrmConfig.getArriveURL(), reqPackedEventData));
                    postArrive.setPriority(Thread.MAX_PRIORITY);
                    postArrive.start();

                } catch (Exception e) {
                    StringWriter error = new StringWriter();
                    e.printStackTrace(new PrintWriter(error));
                    logger.error(error.toString());
                }
            } else if (currDataMap.get(tagKey).getEvent_name().equals(EventName.ARRIVE)) {
//            packedEventData.setEvent_name(EventName.REPORT);
                currDataMap.put(tagKey, packedEventData);
            } else if (currDataMap.get(tagKey).getEvent_name().equals(EventName.REPORT)) {
                currDataMap.put(tagKey, packedEventData);
            } else {
                logger.info("unexpected reader:{} epc:{} event_name:{}",
                        packedEventData.getDevice_name(), packedEventData.getEpc(), currDataMap.get(tagKey).getEvent_name());
            }

            if (!packedEventData.getEvent_name().equalsIgnoreCase(EventName.REPORT)) {
                logger.info("reader:{} antenna:{} epc:{} event_name:{} time:{}",
                        packedEventData.getDevice_name(),
                        packedEventData.getAntenna(), packedEventData.getEpc(),
                        packedEventData.getEvent_name(), packedEventData.getTime());

                logDb.addNormalEvent(packedEventData);
            }

            tagKey = null;
        }

    }

    class EventObj implements IEventDataListener {
        @Override
        public void EventFound(Object sender, EventData eventData) {

            if (eventData.getType() == EventType.EVENT_TAG_REPORT &&
                    eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC) != null) {

                NormalEventData normalEventData = new NormalEventData();

                String epcData = eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC);

                if (tagPatternList.size() == 0) {
                    normalEventData.setVerified(true);
                } else {
                    for (String patternPrefix : tagPatternList) {
                        if (epcData.startsWith(patternPrefix)) {
                            normalEventData.setVerified(true);
                            break;
                        }
                    }
                }

                if (normalEventData.isVerified()) {
                    {
                        event_fmt_time = dstSdf.format(new Date());

                        normalEventData.setDevice_name(rrmConfig.getReaderName());
                        normalEventData.setEvent_name(EventName.REPORT);
                        normalEventData.setEpc(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC));

                        if (eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_TID) == null) {
                            logger.info("reader:{} epc:{} and tid is null!", getDeviceName(), eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC));
                            normalEventData.setTid("");
                        } else
                            normalEventData.setTid(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_TID));

                        normalEventData.setAntenna(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_ANTENNA));
                        normalEventData.setRssi(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_RSSI));
                        normalEventData.setTime(event_fmt_time);
                        normalEventData.setpLatestTimeMillis(System.currentTimeMillis());

                        add(normalEventData);
                    }
                } else {
                    logger.info("reader:{} drop non-verified tag data! epc:{}", getDeviceName(), epcData);
                    return;
                }
            } else if (eventData.getType() == EventType.EVENT_ERROR) {

                String cause = eventData.getParameter(EventType.PARAMS_ERROR.KEY_CAUSE);
                logger.info("reader:{} module event is detecting:{}", getDeviceName(), eventData.toString());

                AlarmEventData alarmEventData = new AlarmEventData();
                event_fmt_time = dstSdf.format(new Date());

                if (cause != null) {
                    if (cause.equalsIgnoreCase(EventType.PARAMS_ERROR.VAL_CAUSE_ANTENNA)) {
                        readerClient.use(Command.ANTENNA_DETECT).run();

                        String antennaList = readerClient.getResult().retString();


                        logger.info("reader:{} antenna loss! alive antenna list:{}", getDeviceName(), antennaList);

                        alarmEventData.setDevice_name(rrmConfig.getReaderName());
                        alarmEventData.setEvent_name("alarm");
                        alarmEventData.setAlarm_code("-1");
                        alarmEventData.setAlarm_reason(eventData.toString());
                        alarmEventData.setTime(event_fmt_time);

                        new Thread(new PostAlarmData(rrmConfig.getAlarmURL(), alarmEventData)).start();

                    } else if (cause.equalsIgnoreCase(EventType.PARAMS_ERROR.VAL_CAUSE_TEMPERATURE)) {
                        logger.info("reader:{} temperature is too high!", getDeviceName());

                        alarmEventData.setDevice_name(rrmConfig.getReaderName());
                        alarmEventData.setEvent_name("alarm");
                        alarmEventData.setAlarm_code("-2");
                        alarmEventData.setAlarm_reason("Temperature maybe dangerous!");
                        alarmEventData.setTime(event_fmt_time);

                        new Thread(new PostAlarmData(rrmConfig.getAlarmURL(), alarmEventData)).start();
                    } else
                        logger.info("reader:{} unknown error!", getDeviceName());
                } else {
                    logger.info("error event is raised and cause content is null!");
                }
            } else {
                if ( eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC) == null)
                    logger.info("reader:{} epc data is null! ", getDeviceName());
                logger.info("reader:{} unknown event type:{} ", getDeviceName(), eventData.getType());
            }
        }

//        @Override
        public void Notified(Object o, String s) {
            logger.error("reader:{} notified:{}", getDeviceName(), s);

            if (s.equalsIgnoreCase("ntf_disconn"))
                setConnected(false);
        }
    }
}
