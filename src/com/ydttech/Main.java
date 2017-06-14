package com.ydttech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import com.ydttech.Middleware.*;
import org.tanukisoftware.wrapper.WrapperListener;
import org.tanukisoftware.wrapper.WrapperManager;

//public class Main implements WrapperListener {
public class Main {

    private static Logger logger = LoggerFactory.getLogger("Main");

    private static boolean isOffline = false;

    private static AtomicBoolean inited = new AtomicBoolean(false);
    private static AtomicBoolean started = new AtomicBoolean(false);

    private static Middleware middleware;

    public static Integer start(String[] args) {
        logger.info("Main application in initializing!");

        middleware = new Middleware();
        Package objPackage = middleware.getClass().getPackage();

        logger.info("SpecificationTitle: " + objPackage.getSpecificationTitle());
        logger.info("SpecificationVersion: " + objPackage.getSpecificationVersion());
        logger.info("SpecificationVendor: " + objPackage.getSpecificationVendor());
        logger.debug("ImplementationTitle: " + objPackage.getImplementationTitle());
        logger.info("ImplementationVersion: " + objPackage.getImplementationVersion());
        logger.info("ImplementationVendor: " + objPackage.getImplementationVendor());

        inited.set(middleware.init());
        if (!inited.get()) {
            logger.info("Middleware initial is not ok!");
            System.exit(1);
        } else {
            logger.info("Middleware initialized is ok!");
        }

        middleware.start();

        return null;
    }

    public int stop( int exitCode )
    {
//        m_app.stop();

        return exitCode;
    }

    public void controlEvent( int event )
    {
        if ( ( event == WrapperManager.WRAPPER_CTRL_LOGOFF_EVENT )
                && ( WrapperManager.isLaunchedAsService() || WrapperManager.isIgnoreUserLogoffs() ) )
        {
            // Ignore
        }
        else
        {
            WrapperManager.stop( 0 );
            // Will not get here.
        }
    }

    public static void main(String[] args) {

//        WrapperManager.start( new Main(), args );
        start(args);
    }
}
