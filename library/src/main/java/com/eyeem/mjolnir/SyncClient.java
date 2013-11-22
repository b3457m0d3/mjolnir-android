package com.eyeem.mjolnir;

import android.text.TextUtils;
import android.util.Log;

import com.android.volley.Request;
import com.squareup.okhttp.OkHttpClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import javax.net.ssl.SSLContext;

/**
 * Created by vishna on 22/11/13.
 */
public class SyncClient {

   RequestBuilder rb;

   public SyncClient(RequestBuilder rb) {
      this.rb = rb;
   }

   public JSONObject json() throws Exception {
      return new JSONObject(raw());
   }

   public <E extends Object> E objectOf(Class clazz) throws Exception {
      return (E) ObjectRequest.fromJSON(clazz, rb.declutter == null ? json() : rb.declutter.jsonObject(json()));
   }

   public <E extends List> E  listOf(Class clazz) throws Exception {
      return (E) ListRequest.fromArray(clazz, rb.declutter.jsonArray(json()));
   }

   public String raw() throws Exception {
      HttpURLConnection connection = buildConnection();
      if (rb.method == Request.Method.PUT || rb.method == Request.Method.POST) {
         if (!TextUtils.isEmpty(rb.content)) {
            final byte[] bytes = rb.content.getBytes("UTF-8");
            connection.setRequestProperty("Content-Length", Integer.toString(bytes.length));
            connection.setRequestProperty("Content-Type", rb.content_type);
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            try {
               os.write(bytes);
               os.flush();
            } finally {
               os.close();
            }
         }
      }
      return readConnection(connection);
   }

   protected HttpURLConnection buildConnection() throws IOException {
      URL url = new URL(rb.toUrl());
      OkHttpClient client = new OkHttpClient();
      SSLContext sslContext; // workaround for okhttp issue #184, should be fixed in 2.0
      try {
         sslContext = SSLContext.getInstance("TLS");
         sslContext.init(null, null, null);
      } catch (GeneralSecurityException e) {
         throw new AssertionError(); // The system has no TLS. Just give up.
      }
      client.setSslSocketFactory(sslContext.getSocketFactory());
      client.setConnectTimeout(Constants.CONNECTION_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
      client.setReadTimeout(Constants.CONNECTION_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
      HttpURLConnection connection = client.open(url);

      connection.setRequestProperty("Accept-Encoding", "gzip");
      connection.setRequestMethod(rb.method());

      // headers
      for (Map.Entry<String, String> header : rb.headers.entrySet() ) {
         connection.setRequestProperty(header.getKey(), header.getValue());
      }

      connection.setUseCaches(false);

      return connection;
   }

   public String readConnection(HttpURLConnection connection) throws Exception {
      int code = 0;

      try {
         code = connection.getResponseCode();

         if (code >= 500 && code < 600)
            throw new Exception(String.format("%d : %s", code, rb.toUrl()));
         if (Constants.DEBUG && code / 200 != 2)
            Log.i(Constants.TAG, String.format("%d : %s", code, rb.toUrl()));

         InputStream is = (code == 400 || code == 401 || code == 403) ? connection.getErrorStream() : connection.getInputStream();

         final String encoding = connection.getContentEncoding();
         if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
            is = new GZIPInputStream(is);
         }

         final String s = convertStreamToString(is);

         if (code < 200 || code >= 300) {
            throw new Exception(String.format("%d : %s", code, rb.toUrl()));
         }

         if (Constants.DEBUG)
            Log.v(Constants.TAG, String.format("[OK] %d bytes read : %s", s.length(), rb.toUrl()));

         return s;
      } catch (Exception e) {
         throw e;
      } finally {
         connection.disconnect();
      }
   }

   private String convertStreamToString(InputStream is) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(is));
      StringBuilder sb = new StringBuilder();

      String line;
      try {
         while ((line = reader.readLine()) != null) {
            sb.append(line);
         }
      } catch (IOException e) {
         e.printStackTrace();
      } finally {
         try {
            is.close();
         } catch (IOException e) {
            if (Constants.DEBUG)
               Log.e(Constants.TAG, "SyncClient.convertToStream()", e);
         }
      }
      return sb.toString();
   }
}
