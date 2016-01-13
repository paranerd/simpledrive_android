package simpledrive.lib;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Connection {
    private static CloseableHttpClient client;

    public synchronized static CloseableHttpClient getThreadSafeClient() {
        if (client != null) {
            return client;
        }

        try {
            HttpClientBuilder httpClient1 = HttpClients.custom();

            // Setup SSL
            SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            }).build();

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", sslSocketFactory)
                    .build();

            PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connMgr.setMaxTotal(200);
            connMgr.setDefaultMaxPerRoute(100);

            // Setup config
            RequestConfig.Builder requestBuilder = RequestConfig.custom()
                    .setConnectTimeout(10000)
                    .setConnectionRequestTimeout(10000);

            // Apply SSL
            httpClient1.setSslcontext(sslContext);

            // Apply config
            httpClient1.setDefaultRequestConfig(requestBuilder.build());
            httpClient1.setMaxConnTotal(10);

            // Apply Connection Manager
            httpClient1.setConnectionManager(connMgr);

            // Build client
            CloseableHttpClient closeable = httpClient1.build();
            client = closeable;

            return closeable;
        } catch (Exception exception) {
            return null;
        }
    }

    public static HashMap<String, String> call(String url, HashMap<String, String> data) {
        /*try {
            URL server = new URL(url);
            HttpURLConnection con = (HttpURLConnection) server.openConnection();

            con.setDoOutput(true);
            con.setChunkedStreamingMode(0);

            OutputStream out = new BufferedOutputStream(con.getOutputStream());
            writeStream(out);

            InputStream is2 = new BufferedInputStream(con.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        InputStream is;
        String result;
        HashMap<String, String> map = new HashMap<>();
        CloseableHttpClient httpClient = getThreadSafeClient();

        Log.i("url", url);
        Log.i("data", data.toString());

        try {
            if(httpClient == null) {
                map.put("status", "error");
                map.put("msg", "An error occured");
                return map;
            }
            HttpPost httpPost = new HttpPost(url);

            // add your data
            /*List<HashMap<String, String>> nameValuePairs = new ArrayList<>(2);
            for (String key : data.keySet()) {
                String value = data.get(key);
                HashMap<String, String> is_new = new HashMap<>();
                is_new.put(key, value);
                nameValuePairs.add(is_new);
            }*/

            // add your data
            List<NameValuePair> nameValuePairs = new ArrayList<>(2);
            for (String key : data.keySet()) {
                String value = data.get(key);
                nameValuePairs.add(new BasicNameValuePair(key, value));
            }

            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            Log.i("before", "execute");
            HttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();

            Log.i("response", response.getStatusLine().toString());

            is = entity.getContent();

            if(response.getStatusLine().getStatusCode() != HttpURLConnection.HTTP_OK || is == null) {
                map.put("status", "error");
                map.put("msg", response.getStatusLine().getReasonPhrase());
                return map;
            }

        } catch (IOException e) {
            e.printStackTrace();
            map.put("status", "error");
            map.put("msg", "Connection error");
            return map;
        }

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is,"iso-8859-1"),8);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            is.close();
            result = sb.toString();

            JSONObject obj = new JSONObject(result);
            map.put("status", "ok");
            map.put("msg", obj.getString("msg"));
            return map;
        } catch (Exception e) {
            map.put("status", "error");
            map.put("msg", "An error occured");
            return map;
        }
    }

    public static void logout(Context ctx) {
        client = null;
        AccountManager am = AccountManager.get(ctx);
        Account aaccount[] = am.getAccounts();
        for (Account anAaccount : aaccount) {
            if (anAaccount.type.equals("org.simpledrive")) {
                am.removeAccount(new Account(anAaccount.name, anAaccount.type), null, null);
            }
        }
    }

    public static class MySSLSocketFactory extends SSLSocketFactory {
        private SSLContext sslContext;

        public Socket createSocket() throws IOException {
            return sslContext.getSocketFactory().createSocket();
        }

        public Socket createSocket(Socket socket, String s, int i, boolean flag) throws IOException {
            return sslContext.getSocketFactory().createSocket(socket, s, i, flag);
        }

        public MySSLSocketFactory(KeyStore keystore) throws KeyManagementException, NoSuchAlgorithmException, UnrecoverableKeyException, KeyStoreException {
            super(keystore);
            sslContext = SSLContext.getInstance("TLS");
            TrustManager tm = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                }

                public X509Certificate[] getAcceptedIssuers() {
                     return null;
                 }
            };
            sslContext.init(null, new TrustManager[] { tm }, null);
        }
    }
}

