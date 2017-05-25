package com.ydttech.vo;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ean.Chung on 2016/10/18.
 */
public class PostAlarmData implements Runnable {

    private static Logger logger = LoggerFactory.getLogger("PostAlarmData");

    String postURL;
    AlarmEventData alarmEventData;
    private boolean enable = true;

    public PostAlarmData(String postURL, AlarmEventData alarmEventData) {
        this.postURL = postURL;
        this.alarmEventData = alarmEventData;

    }

    public void run() {

        if (!postURL.isEmpty()) {
            try {
//            DefaultHttpClient httpClient = new DefaultHttpClient();
                CloseableHttpClient httpClient = HttpClientBuilder.create().build();

                HttpPost postRequest = new HttpPost(postURL);

                List<NameValuePair> qparams = new ArrayList<NameValuePair>();

                String request = "{" +
                        "\"device_name\":\"" + alarmEventData.getDevice_name() + "\"," +
                        "\"alarm_code\":\"" + alarmEventData.getAlarm_code() + "\"," +
                        "\"alarm_reason\":\"" + alarmEventData.getAlarm_reason() + "\"," +
                        "\"time\":\"" + alarmEventData.getTime() + "\"}";

                qparams.add(new BasicNameValuePair("body", request));

                postRequest.setEntity(new UrlEncodedFormEntity(qparams));

                HttpResponse response = httpClient.execute(postRequest);

                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException("Failed : HTTP error code : "
                            + response.getStatusLine().getStatusCode());
                }

                BufferedReader br = new BufferedReader(
                        new InputStreamReader((response.getEntity().getContent())));

                String output;
                logger.info("Post Alarm Req:{}", request);
                while ((output = br.readLine()) != null) {
                    logger.info("Post Alarm Rsp:{}", output);
                }

                httpClient.close();
//            httpClient.getConnectionManager().shutdown();

            } catch (Exception e) {
                StringWriter error = new StringWriter();
                e.printStackTrace(new PrintWriter(error));
                logger.error(error.toString());
            }
        }
    }
}
