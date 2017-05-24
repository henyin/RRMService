package com.ydttech;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
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
public class PostNormalData implements Runnable {

    private static Logger logger = LoggerFactory.getLogger("PostNormalData");

    String postURL;
    NormalEventData normalEventData;
    private boolean enable = true;

    public PostNormalData(String postURL, NormalEventData normalEventData) {
        this.postURL = postURL;
        this.normalEventData = normalEventData;

    }

    public void run() {

        if (!postURL.isEmpty()) {
            try {
//            DefaultHttpClient httpClient = new DefaultHttpClient();
                CloseableHttpClient httpClient = HttpClientBuilder.create().build();
                HttpPost postRequest = new HttpPost(postURL);

                List<NameValuePair> qparams = new ArrayList<NameValuePair>();

                String request = "{" +
                        "\"device_name\":\"" + normalEventData.getDevice_name() + "\"," +
                        "\"epc\":\"" + normalEventData.getEpc() + "\"," +
                        "\"tid\":\"" + normalEventData.getTid() + "\"," +
                        "\"antenna\":\"" + normalEventData.getAntenna() + "\"," +
                        "\"rssi\":\"" + normalEventData.getRssi() + "\"," +
                        "\"time\":\"" + normalEventData.getTime() + "\"}";

                qparams.add(new BasicNameValuePair("body", request));


//                qparams.add(new BasicNameValuePair("lane","T1-3"));
//                qparams.add(new BasicNameValuePair("message","我是reader"));
//                qparams.add(new BasicNameValuePair("color", "GREEN"));
//                postRequest.setEntity(new UrlEncodedFormEntity(qparams, "UTF-8"));

                postRequest.setEntity(new UrlEncodedFormEntity(qparams));

                logger.info("Reader:{} Start sending post to {}  ", normalEventData.getDevice_name(), postURL);
                HttpResponse response = httpClient.execute(postRequest);

                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException("Failed : HTTP error code : "
                            + response.getStatusLine().getStatusCode());
                }

                BufferedReader br = new BufferedReader(
                        new InputStreamReader((response.getEntity().getContent())));

                String output;
                logger.info("Post Normal Req:{} event_name={}", request, normalEventData.getEvent_name());
                while ((output = br.readLine()) != null) {
                    logger.info("Post Normal Rsp:{} event_device={} event_name:{}", output, normalEventData.getDevice_name(), normalEventData.getEvent_name());
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
