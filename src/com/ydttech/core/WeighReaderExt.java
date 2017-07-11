package com.ydttech.core;

import com.ydt.driver.ConnectionException;
import com.ydt.driver.ConnectionType;
import com.ydt.driver.IEventDataListener;
import com.ydt.invoke.InvokeError;
import com.ydt.invoke.InvokeResult;
import com.ydt.log.Report;
import com.ydt.reader.Command;
import com.ydt.reader.ReaderClient;
import com.ydt.types.EventData;
import com.ydt.types.EventType;
import com.ydttech.optc.util.CoilsEventListener;
import com.ydttech.optc.util.DICoilsMessage;
import com.ydttech.optc.util.DOCoilsMessage;
import com.ydttech.optc.util.ModbusUtil;
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

/**
 * Created by Ean.Chung on 2016/10/12.
 */
public class WeighReaderExt implements Runnable {

    private Logger logger = LoggerFactory.getLogger("WeighReader");

    private  EventDataAnalyst eventDataAnalyst;

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

    private boolean isActiveReader = false;
    private boolean isSuspendReader = true;

    private boolean isEntry1Signal = false;
    private boolean isEntry2Signal = false;
    private String entry1Antenna = "";
    private String entry2Antenna = "";
    private StringBuilder enableAntenna = new StringBuilder();

    private NormalEventData latestEventData = new NormalEventData();
    private boolean waitingArrive = false;

    private ModbusUtil weighModbus;
    String ioCtrlId = "Weigh Controller";
    String ioCtrlIp = "localhost";
    private CoilsEventListener coilsEventListener = new CoilsEventListener() {
        @Override
        public void DIChangeEvent(DICoilsMessage diCoilsMessage) {
//            if (diCoilsMessage.getBitVector().getBit(Integer.parseInt(rrmConfig.getEntryDI())) == true) {
//                isActiveReader = true;
//            }
//
//            if (diCoilsMessage.getBitVector().getBit(Integer.parseInt(rrmConfig.getEntryDI())) == false) {
//                isSuspendReader = true;
//            }
        }

        @Override
        public void DOChangeEvent(DOCoilsMessage doCoilsMessage) {
            if (isEntry1Signal == false && doCoilsMessage.getBitVector().getBit(Integer.parseInt(rrmConfig.getEntry1DO())) == true) {
                isEntry1Signal = true;
                logger.info("reader:{} isEntry1Signal:{} isEntry2Signal:{} enableAntenna:{} by trigger!",
                        rrmConfig.getReaderName(), isEntry1Signal, isEntry2Signal, rrmConfig.getEntryPort());
                waitingArrive = true;
                return;
            }

            if (isEntry2Signal == false && doCoilsMessage.getBitVector().getBit(Integer.parseInt(rrmConfig.getEntry2DO())) == true) {
                isEntry2Signal = true;
                logger.info("reader:{} isEntry1Signal:{} isEntry2Signal:{} enableAntenna:{} by trigger!",
                        rrmConfig.getReaderName(), isEntry1Signal, isEntry2Signal, rrmConfig.getEntryPort());
                waitingArrive = true;
                return;
            }

            if (doCoilsMessage.getBitVector().getBit(Integer.parseInt(rrmConfig.getEntry1DO())) == false && isEntry1Signal == true) {
                isEntry1Signal = false;

                if (waitingArrive &&
                        (System.currentTimeMillis() - latestEventData.getpLatestTimeMillis()) < Integer.parseInt(rrmConfig.getDepartureTimeout()) &&
                        rrmConfig.getEntry1Port().indexOf(latestEventData.getAntenna()) != -1) {
                    logger.info("reader:{} trace back to get latest etag data epc:{} to send arrive!",
                            rrmConfig.getReaderName(), latestEventData.getEpc());
                    add(latestEventData);
                } else {
                    logger.info("reader:{} didn't receive suitable eTag data in triggering time!", getDeviceName());
                }
                waitingArrive = false;

                logger.info("reader:{} isEntry1Signal:{} isEntry2Signal:{} enableAntenna:{} by trigger!",
                        rrmConfig.getReaderName(), isEntry1Signal, isEntry2Signal, rrmConfig.getEntryPort());
                return;
            }

            if (doCoilsMessage.getBitVector().getBit(Integer.parseInt(rrmConfig.getEntry2DO())) == false && isEntry2Signal == true) {
                isEntry2Signal = false;

                if (waitingArrive &&
                        (System.currentTimeMillis() - latestEventData.getpLatestTimeMillis()) <= Integer.parseInt(rrmConfig.getDepartureTimeout()) &&
                        rrmConfig.getEntry2Port().indexOf(latestEventData.getAntenna()) != -1) {
                    logger.info("reader:{} trace back to get latest etag data epc:{} to send arrive!",
                            rrmConfig.getReaderName(), latestEventData.getEpc());
                } else {
                    logger.info("reader:{} didn't receive suitable eTag data in triggering time!", getDeviceName());
                }
                waitingArrive = false;

                logger.info("reader:{} isEntry1Signal:{} isEntry2Signal:{} enableAntenna:{} by trigger!",
                        rrmConfig.getReaderName(), isEntry1Signal, isEntry2Signal, rrmConfig.getEntryPort());
                return;
            }


        }
    };

    public WeighReaderExt(final RRMConfig rrmConfig) {
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

    public boolean openWeighModbus() {

        boolean retCode = false;

        ioCtrlId = rrmConfig.getIoCtrlId();
        ioCtrlIp = rrmConfig.getIoCtrlIp();

        weighModbus = new ModbusUtil(ioCtrlId, ioCtrlIp, 8, 8, true);
        weighModbus.setEventTimer(100);

        if (!weighModbus.open()) {
            logger.error("modbus slave:{} connection to {} is failure!", ioCtrlId, ioCtrlIp);
            return retCode;

        } else {
            logger.info("modbus slave:{} connection to {} is successful!", ioCtrlId, ioCtrlIp);
        }

        if (weighModbus.registerEvent(coilsEventListener)) {
            logger.info("register IOController:{} event is successful!", ioCtrlId);
            weighModbus.activeEvent();
            retCode = true;
        } else {
            logger.error("register IOController:{} event is failure!", ioCtrlId);
        }

        return retCode;
    }

    public boolean closeWeighModbus() {

        boolean retCode = false;

        if (weighModbus.standbyEvent()) {
            logger.info("standby IOController:{} event is successful!", ioCtrlId);
            retCode = true;
        } else {
            logger.error("standby IOController:{} event is failure!", ioCtrlId);
        }

        weighModbus.close();

        return retCode;

    }

    public int setAntMux(String antenna) {

        int retCode = 0;

        InvokeResult invokeResult =  readerClient.use(Command.ANTENNA_SET_MUX)
                .with("antenna", antenna)
                .run();

        retCode = invokeResult.error();

        return retCode;
    }

    public int connect(int timeout) {

        int retCode = InvokeError.OK;

        try {
            if (readerClient != null) {
                readerClient.close();
            }

            readerClient.open();
            readerClient.setTimeout(timeout);
            lastConnectedTime = System.currentTimeMillis();
            setConnected(true);

        } catch (Exception e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            retCode = InvokeError.FAIL;
            setConnected(false);
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

                readerClient.use(Command.ANTENNA_SET_POWER)
                        .with("antenna", id)
                        .with("power", "0")
                        .run();
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
        openWeighModbus();

        if (!(connect(connBrokenTimeoutLimit) == InvokeError.OK)) {
            logger.error("reader:{} connecting to {} is failure!", rrmConfig.getReaderName(), rrmConfig.getIpAddr());
        }

        if (login() != InvokeError.OK) {
            logger.error("reader:{} logging is failure!", rrmConfig.getReaderName());
        }

        if (standby() != InvokeError.OK) {
            logger.error("reader:{} standby event mode is failure!", rrmConfig.getReaderName());
        }

        if (setAntennaPort() != InvokeError.OK) {
            logger.error("reader:{} set antenna port power is failure!", rrmConfig.getReaderName());
        }

        if (setAntMux(rrmConfig.getEntryPort()) != InvokeError.OK) {
            logger.error("reader:{} set antenna mux:{} is failure", rrmConfig.getReaderName(), rrmConfig.getEntryPort());
        }

        if (setEventDataFmt() != InvokeError.OK) {
            logger.info("reader:{} set event data format is failure", rrmConfig.getReaderName());
        }

        if (activity() != InvokeError.OK) {
            logger.info("reader:{} active into event mode is failure!", rrmConfig.getReaderName());
        }

        while(true) {

            try {

                if (!isConnected()) {
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

                    if (!(connect(connBrokenTimeoutLimit) == InvokeError.OK)) {
                        logger.error("reader:{} connecting to {} is failure!", rrmConfig.getReaderName(), rrmConfig.getIpAddr());
                        Thread.sleep(connBrokenTimeoutLimit);
                        continue;
                    }

                    if (login() != InvokeError.OK) {
                        logger.error("reader:{} logging is failure!", rrmConfig.getReaderName());
                        continue;
                    }

                    if (standby() != InvokeError.OK) {
                        logger.error("reader:{} standby event mode is failure!", rrmConfig.getReaderName());
                        continue;
                    }

                    if (setAntMux(rrmConfig.getEntryPort()) != InvokeError.OK) {
                        logger.error("reader:{} set antenna mux:{} is failure", rrmConfig.getReaderName(), rrmConfig.getEntryPort());
                        continue;
                    }

                    if (setEventDataFmt() != InvokeError.OK) {
                        logger.info("reader:{} set event data format is failure", rrmConfig.getReaderName());
                        continue;
                    }

                } else {
                    logger.info("reader:{} isEntry1Signal:{} isEntry2Signal:{}", rrmConfig.getReaderName(), isEntry1Signal, isEntry2Signal);
                }
                Thread.sleep(connBrokenTimeoutLimit);
            } catch (Exception e) {
                logger.error("main loop exception:{}", e.getMessage());
            }

        }
    }

    class SelfCheckJob extends TimerTask  {

        private void checkIOCtrl() {
            if (!weighModbus.isAlive())
                openWeighModbus();
        }

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

        private void checkAntenna() {
            int retCode = 0;

            InvokeResult invokeResult =  readerClient.use(Command.ANTENNA_DETECT)
                    .run();

            logger.info("reader:{} Get connected antenna ports:[{}]", getDeviceName(), invokeResult.retString());

        }

        @Override
        public void run() {

            try {
                checkIOCtrl();

                if (isConnected()) {
//                    if (getCurrConnBrokenTimes() >= 2) {
//                        setCurrConnBrokenTimes(0);
//                        setRebootFlag(true);
//                        logger.info("Reader:{} reset network flag:{} next time will be reset!", getDeviceName(), isRebootFlag());
//                    }

                    if (currDataMap.isEmpty()) {
                        checkTemperature();
                        purgeLogDB();
                        getOperatingMode();
//                        checkAntenna();
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

//        synchronized (this) {
        {
            if (!currDataMap.containsKey(tagKey) && packedEventData.getEpc() != null) {
                packedEventData.setEvent_name(EventName.ARRIVE);
                try {
                    waitingArrive = false;
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
                currDataMap.put(tagKey, packedEventData);
            } else if (currDataMap.get(tagKey).getEvent_name().equals(EventName.REPORT)) {
                currDataMap.put(tagKey, packedEventData);
            } else {
                logger.info("unexpected reader:{} epc:{} event_name:{}",
                        packedEventData.getDevice_name(), packedEventData.getEpc(), currDataMap.get(tagKey).getEvent_name());
            }

            if (!packedEventData.getEvent_name().equalsIgnoreCase(EventName.REPORT)) {
                logger.info("reader:{} antenna:{} epc:{} readCount:{} event_name:{} time:{}",
                        packedEventData.getDevice_name(),
                        packedEventData.getAntenna(), packedEventData.getEpc(),
                        packedEventData.getReadCount(),
                        packedEventData.getEvent_name(), packedEventData.getTime());

                logDb.addNormalEvent(packedEventData);
            }

            tagKey = null;
        }

    }

    class EventObj implements IEventDataListener {
        @Override
        public void EventFound(Object sender, EventData eventData) {

            event_fmt_time = dstSdf.format(new Date());

            if (eventData.getType() == EventType.EVENT_ERROR) {

                String cause = eventData.getParameter(EventType.PARAMS_ERROR.KEY_CAUSE);
                logger.info("reader:{} module event is detecting:{}", getDeviceName(), eventData.toString());

                AlarmEventData alarmEventData = new AlarmEventData();

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
            }

            if (eventData.getType() == EventType.EVENT_TAG_REPORT &&
                    eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC) != null) {

                boolean isVerified = false;

                if (tagPatternList.size() == 0) {
                    isVerified = true;
                } else {
                    for (String patternPrefix : tagPatternList) {
                        if (eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC).startsWith(patternPrefix)) {
                            isVerified = true;
                            break;
                        }
                    }
                }

                if (isVerified) {

                    latestEventData.setDevice_name(rrmConfig.getReaderName());
                    latestEventData.setEvent_name(EventName.REPORT);
                    latestEventData.setEpc(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC));
                    latestEventData.setVerified(true);

                    if (eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_TID) == null) {
                        latestEventData.setTid("");
                    } else
                        latestEventData.setTid(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_TID));

                    latestEventData.setAntenna(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_ANTENNA));
                    latestEventData.setRssi(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_RSSI));
                    latestEventData.setTime(event_fmt_time);
                    latestEventData.setpLatestTimeMillis(System.currentTimeMillis());
                    latestEventData.setReadCount(Integer.parseInt(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_READCOUNT)));
                }
            }

            if (!isEntry1Signal && !isEntry2Signal) {
                return;
            }

            synchronized (this) {
                if (eventData.getType() == EventType.EVENT_TAG_REPORT &&
                        eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC) != null) {

                    NormalEventData normalEventData = new NormalEventData();

                    if (tagPatternList.size() == 0) {
                        normalEventData.setVerified(true);
                    } else {
                        for (String patternPrefix : tagPatternList) {
                            if (eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC).startsWith(patternPrefix)) {
                                normalEventData.setVerified(true);
                                break;
                            }
                        }
                    }

                    if (normalEventData.isVerified()) {

                        String antennaData = eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_ANTENNA);
                        if (antennaData == null)
                            return;

                        if (rrmConfig.getEntry1Port().indexOf(antennaData) != -1) {
                            if (!isEntry1Signal)
                                return;
                            else
                                isEntry1Signal = false;
                        }

                        if (rrmConfig.getEntry2Port().indexOf(antennaData) != -1) {
                            if (!isEntry2Signal)
                                return;
                            else
                                isEntry2Signal = false;
                        }

                        normalEventData.setDevice_name(rrmConfig.getReaderName());
                        normalEventData.setEvent_name(EventName.REPORT);
                        normalEventData.setEpc(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC));
                        normalEventData.setVerified(true);

                        if (eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_TID) == null) {
                            normalEventData.setTid("");
                        } else
                            normalEventData.setTid(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_TID));

                        normalEventData.setAntenna(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_ANTENNA));
                        normalEventData.setRssi(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_RSSI));
                        normalEventData.setTime(event_fmt_time);
                        normalEventData.setpLatestTimeMillis(System.currentTimeMillis());
                        normalEventData.setReadCount(Integer.parseInt(eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_READCOUNT)));
                        logger.info("reader:{} recv verified tag data! epc:{} event_time:{}",
                                getDeviceName(), eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC),
                                event_fmt_time);
                        add(normalEventData);
                    } else {
                        logger.info("reader:{} drop non-verified tag data! epc:{} event_time:{}",
                                getDeviceName(), eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC),
                                event_fmt_time);
                        return;
                    }
                } else {
                    if (eventData.getParameter(EventType.PARAMS_TAG_REPORT.KEY_EPC) == null)
                        logger.info("reader:{} epc data is null! ", getDeviceName());
                }
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