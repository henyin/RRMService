package com.ydttech;

import com.ydt.driver.ConnectionException;
import com.ydt.driver.ConnectionType;
import com.ydt.invoke.InvokeError;
import com.ydt.invoke.InvokeResult;
import com.ydt.log.Report;
import com.ydt.reader.ReaderClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Ean.Chung on 2016/12/14.
 */
public class A800 extends ReaderClient implements Runnable {

    private static Logger logger = LoggerFactory.getLogger("A800");

    private RRMConfig rrmConfig;



    public A800(int type, String uri, RRMConfig rrmConfig) throws ConnectionException {
        super(type, uri);
        this.rrmConfig = rrmConfig;
        this._uri = rrmConfig.getIpAddr();
        logger.info("test");

    }

    public InvokeResult begin() throws ConnectionException {

        logger.info("begin");
        setTimeout(3000);
        open();
        return this._invoker.getResult();
    }

    @Override
    public void run() {
        try {
            InvokeResult ir = this.begin();
            logger.info("Invoke Result:{}", ir.error());
        } catch (ConnectionException e) {
            logger.info("e:{}", e);
        }
    }
}
