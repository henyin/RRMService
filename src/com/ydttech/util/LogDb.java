package com.ydttech.util;

import com.ydttech.vo.AlarmEventData;
import com.ydttech.vo.NormalEventData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.*;

/**
 * Created by Ean.Chung on 2016/10/18.
 */
public class LogDb {

    private Logger logger = LoggerFactory.getLogger("LogDb");

    private String dbURL;
    private Connection conn;
    private Statement stmt;
    private PreparedStatement preStmt;
    private StringBuilder sqlBuilder;

    private final String createNormal = "CREATE TABLE IF NOT EXISTS NormalEvent (" +
            "event_sn integer PRIMARY KEY AUTOINCREMENT , " +
            "device_name  text(32), " +
            "epc text(128), " +
            "tid text(128), " +
            "antenna integer, " +
            "rssi integer, " +
            "event_name text(64), " +
            "time text(32));";

    private final String createAlarm = "CREATE TABLE IF NOT EXISTS AlarmEvent (" +
            "event_sn integer PRIMARY KEY AUTOINCREMENT , " +
            "device_name  text(32), " +
            "alarm_code text(128), " +
            "alarm_reason text(128), " +
            "time text(32));pragma synchronous=off;PRAGMA journal_mode = OFF;";

    private final String offSynchronous = "PRAGMA synchronous = OFF";
    private final String offJournalMode = "PRAGMA journal_mode = OFF";

//    static {
//        try {
//            Class.forName("org.sqlite.JDBC");
//        } catch (Exception e) {
//            StringWriter error = new StringWriter();
//            e.printStackTrace(new PrintWriter(error));
//            logger.error(error.toString());
//        }
//    }

    public LogDb(String dbURL) {

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (Exception e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            logger.error(error.toString());
        }

        this.dbURL = dbURL;
    }

    public void init() {

        String filePath = dbURL.split(":")[2];
        String folderPath = filePath.substring(0, filePath.lastIndexOf("/"));

        if (!new File(folderPath).exists()) {
            new File(folderPath).mkdirs();
            logger.info("Create sqlite database folder:{}", folderPath);
        } else
            logger.info("sqlite database folder:{} exist!", folderPath);

        logger.info("Start initial SQLite db file in dbURL:{}", dbURL);

        getConnRes();

        doSql(createNormal);
        doSql(createAlarm);
//        freeConnRes();
    }

    private void getConnRes() {
        try {
            if (conn == null || conn.isClosed()) {
                conn = DriverManager.getConnection(dbURL);
                conn.setAutoCommit(true);

                if (stmt == null || stmt.isClosed())
                    stmt = conn.createStatement();

                doSql(offSynchronous);
                doSql(offJournalMode);
            }
        } catch (Exception e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            logger.error(error.toString());
        }
    }

    private void freeConnRes() {
        try {
            stmt.close();
            conn.close();
        } catch (Exception e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            logger.error(error.toString());
        }
    }

    public void doSql(String sqlStr) {
        try {
            stmt.execute(sqlStr);
            stmt.close();
        } catch (Exception e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            logger.error(error.toString());
        }
    }

    public void addNormalEvent(NormalEventData normalEventData) {
        try {
            getConnRes();

            sqlBuilder = new StringBuilder("Insert Into NormalEvent ( ");
            sqlBuilder.append("device_name, epc, tid, antenna, rssi, event_name, time) ");
            sqlBuilder.append("Values ( ?, ?, ?, ?, ?, ?, ?);");
            preStmt = conn.prepareStatement(sqlBuilder.toString());
            preStmt.setString(1, normalEventData.getDevice_name());
            preStmt.setString(2, normalEventData.getEpc());
            preStmt.setString(3, normalEventData.getTid());
            preStmt.setInt(4, Integer.parseInt(normalEventData.getAntenna()));
            preStmt.setInt(5, Integer.parseInt(normalEventData.getRssi()));
            preStmt.setString(6, normalEventData.getEvent_name());
            preStmt.setString(7, normalEventData.getTime());
            preStmt.execute();
            sqlBuilder.setLength(0);
            stmt.close();
            preStmt = null;
            sqlBuilder = null;

//            freeConnRes();
        } catch (Exception e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            logger.error(error.toString());
        }
    }

    public void addAlarmEvent(AlarmEventData alarmEventData) {
        try {
            getConnRes();

            sqlBuilder = new StringBuilder("Insert Into AlarmEvent ( ");
            sqlBuilder.append("device_name, alarm_code, alarm_reason, time) ");
            sqlBuilder.append("Values ( ?, ?, ?, ? );");
            preStmt = conn.prepareStatement(sqlBuilder.toString());
            preStmt.setString(1, alarmEventData.getDevice_name());
            preStmt.setString(2, alarmEventData.getAlarm_code());
            preStmt.setString(3, alarmEventData.getAlarm_reason());
            preStmt.setString(4, alarmEventData.getTime());
            preStmt.execute();
            sqlBuilder.setLength(0);
            preStmt.close();
            preStmt = null;
            sqlBuilder = null;

//            freeConnRes();
        } catch (Exception e) {
            StringWriter error = new StringWriter();
            e.printStackTrace(new PrintWriter(error));
            logger.error(error.toString());
        }
    }
}
