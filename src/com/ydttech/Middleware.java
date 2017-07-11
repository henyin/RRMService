package com.ydttech;

import com.ydttech.core.BarrierReader;
import com.ydttech.core.ReaderDev;
import com.ydttech.core.WeighReader;
import com.ydttech.core.WeighReaderExt;
import com.ydttech.vo.InvokeType;
import com.ydttech.vo.RRMConfig;
import org.dom4j.Document;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Ean.Chung on 2016/10/12.
 */
public class Middleware {

    private static Logger logger = LoggerFactory.getLogger("Middleware");

    final private String PROP_CFG_PARM = "CFG_XML";
    final private String ROOT_NODE = "/RRMiddleware";

    private String initCfgDirectory = "";
    private String initCfgFilename = "rrm.xml";
    private String initCfg = initCfgDirectory+initCfgFilename;

    String arriveURL, departureURL, alarmURL;

    private List<RRMConfig> rrmConfigList = new ArrayList<RRMConfig>();

    private String dbURL, purgeDay;

    private List<String> patternList = new ArrayList<String>();

    public static AtomicBoolean getStarted() {
        return started;
    }

    public static void setStarted(AtomicBoolean started) {
        Middleware.started = started;
    }

    private static AtomicBoolean started = new AtomicBoolean(false);

    public boolean init() {

        if (readCfg() == 0)
            return true;
        else
            return  false;
    }

    public boolean start() {

        logger.info("Middleware is running!");

        try {
            for (RRMConfig rrmConfig : rrmConfigList) {
                if (rrmConfig.getInvokeType().equalsIgnoreCase(InvokeType.INVOKE_TYPE_NORMAL))
                    new Thread(new ReaderDev((rrmConfig))).start();
                else if (rrmConfig.getInvokeType().equalsIgnoreCase(InvokeType.INVOKE_TYPE_BARRIER))
                    new Thread(new BarrierReader((rrmConfig))).start();
                else if (rrmConfig.getInvokeType().equalsIgnoreCase(InvokeType.INVOKE_TYPE_WEIGH))
                    new Thread(new WeighReaderExt((rrmConfig))).start();
                else
                    new Thread(new ReaderDev((rrmConfig))).start();
            }

        } catch (Exception e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            logger.error(error.toString());
        }

        return true;
    }

    private int readCfg() {

        int retCode = 0;

        Document docCfgXml = null;

        SAXReader reader = new SAXReader();
        Properties props = System.getProperties();

        if (props.getProperty(PROP_CFG_PARM) != null) {
            initCfg = props.getProperty(PROP_CFG_PARM);
        }

        try {
            docCfgXml = reader.read(new File(initCfg));
        } catch (Exception e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            logger.error(error.toString());
            retCode = -1;
        }

        List<Node> nodes;

        if (retCode == 0) {
            nodes = docCfgXml.selectNodes(ROOT_NODE + "/DBConfig");

            for (Node node : nodes) {
                dbURL = node.valueOf("DBURL");
                purgeDay = node.valueOf("PurgeDay") == "" ? "150" : node.valueOf("PurgeDay");
            }

        }

        if (retCode == 0) {
            nodes = docCfgXml.selectNodes(ROOT_NODE + "/TagFilter");

            for (Node node : nodes) {
                List<Node> patternNodes = node.selectNodes("Pattern");
                for (Node patternNode : patternNodes) {
                    if (!patternNode.getText().isEmpty())
                        patternList.add(patternNode.getText());
                }

            }
        }

        if (retCode == 0) {
            nodes = docCfgXml.selectNodes(ROOT_NODE + "/EventPostURL");

            for (Node node : nodes) {
                arriveURL = node.valueOf("ArriveEventPostURL");
                departureURL = node.valueOf("DepartureEventPostURL");
                alarmURL = node.valueOf("AlarmEventPostURL");
            }
        }

        if (retCode == 0) {
             nodes = docCfgXml.selectNodes(ROOT_NODE + "/ReaderConfig/Reader");

            for (Node node : nodes) {

                try {
                    RRMConfig rrmConfig = new RRMConfig();
                    rrmConfig.setReaderName(node.valueOf("@name"));
                    rrmConfig.setIpAddr(node.valueOf("@ip"));
                    rrmConfig.setDepartureTimeout(node.valueOf("@departureTimeout"));
                    rrmConfig.setReadCount(node.valueOf("@readCount"));

                    Node invokeNode = node.selectSingleNode("Invoke");
                    if (invokeNode != null) {
                        rrmConfig.setInvokeType(invokeNode.valueOf("@type"));
                        rrmConfig.setIoCtrlIp(invokeNode.valueOf("@ioCtrlIp"));
                        rrmConfig.setIoCtrlId(invokeNode.valueOf("@ioCtrlId"));
                        if (rrmConfig.getInvokeType().equalsIgnoreCase(InvokeType.INVOKE_TYPE_BARRIER)) {
                            rrmConfig.setEntryDI(invokeNode.valueOf("@entryDI"));
                            rrmConfig.setEntryPort(invokeNode.valueOf("@entryPort"));
                            if (rrmConfig.getReadCount().equalsIgnoreCase(""))
                                rrmConfig.setReadCount("15");
                        } else if (rrmConfig.getInvokeType().equalsIgnoreCase(InvokeType.INVOKE_TYPE_WEIGH)) {
                            rrmConfig.setEntryDI("0");
                            rrmConfig.setEntryPort("0");
                            rrmConfig.setEntry1DO(invokeNode.valueOf("@entry1DO"));
                            rrmConfig.setEntry2DO(invokeNode.valueOf("@entry2DO"));
                            rrmConfig.setEntry1Port(invokeNode.valueOf("@entry1Port"));
                            rrmConfig.setEntry2Port(invokeNode.valueOf("@entry2Port"));
                            rrmConfig.setEntryPort(rrmConfig.getEntry1Port() + " " + rrmConfig.getEntry2Port());
                        }
                    } else {
                        rrmConfig.setInvokeType(InvokeType.INVOKE_TYPE_NORMAL);
                    }


                    List<Node> portNodes = node.selectNodes("AntennaPort");

                    Map<String, String> antMap = new HashMap<>();

                    for (Node portNode : portNodes) {
                        antMap.put(portNode.valueOf("@id"), portNode.valueOf("@power"));
                    }
                    rrmConfig.setPower(antMap);

                    rrmConfig.setMinTemperature(node.selectSingleNode("TemperatureLimit").valueOf("@min"));
                    rrmConfig.setMaxTemperature(node.selectSingleNode("TemperatureLimit").valueOf("@max"));

                    rrmConfig.setArriveURL(arriveURL);
                    rrmConfig.setDepartureURL(departureURL);
                    rrmConfig.setAlarmURL(alarmURL);

                    rrmConfig.setConnBrokenTimeoutLimit(node.selectSingleNode("ConnBrokenLimit").valueOf("@timeout"));
                    rrmConfig.setConnBrokenTimesLimit(node.selectSingleNode("ConnBrokenLimit").valueOf("@times"));

                    rrmConfig.setDbURL(dbURL);
                    rrmConfig.setPurgeDay(purgeDay);
                    rrmConfig.setTagPatternList(patternList);

                    logger.info("Reader:{} Config :{}", rrmConfig.getReaderName(), rrmConfig.toString() );

                    rrmConfigList.add(rrmConfig);

                } catch (Exception ex) {
                    StringWriter error = new StringWriter();
                    ex.printStackTrace(new PrintWriter(error));
                    logger.error(error.toString());
                    retCode = -1;
                }
            }

        }

        return retCode;
    }
}
