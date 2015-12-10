package simpledrive.lib;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.util.Log;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Connection {
    private static DefaultHttpClient client;

    public synchronized static DefaultHttpClient getThreadSafeClient() {
        if (client != null) {
            return client;
        }

        try {
            KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
            keystore.load(null, null);
            MySSLSocketFactory mysslsocketfactory = new MySSLSocketFactory(keystore);
            mysslsocketfactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            BasicHttpParams basichttpparams = new BasicHttpParams();
            HttpProtocolParams.setVersion(basichttpparams, HttpVersion.HTTP_1_1);
            HttpProtocolParams.setContentCharset(basichttpparams, "UTF-8");
            HttpConnectionParams.setConnectionTimeout(basichttpparams, 3000);
            HttpConnectionParams.setSoTimeout(basichttpparams, 5000);
            SchemeRegistry schemeregistry = new SchemeRegistry();
            schemeregistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            schemeregistry.register(new Scheme("https", mysslsocketfactory, 443));
            ClientConnectionManager ccm = new ThreadSafeClientConnManager(basichttpparams, schemeregistry);
            client = new DefaultHttpClient(ccm, basichttpparams);
            return client;
        } catch (Exception exception) {
            return new DefaultHttpClient();
        }
    }

    public static HashMap<String, String> call(String url, HashMap<String, String> data) {
        InputStream is;
        String result;
        HashMap<String, String> map = new HashMap<>();
        map.put("status", "error");
        map.put("msg", "An error occured");

        try {
            DefaultHttpClient httpClient = Connection.getThreadSafeClient();
            HttpPost httpPost = new HttpPost(url);

            // add your data
            List<NameValuePair> nameValuePairs = new ArrayList<>(2);
            for (String key : data.keySet()) {
                String value = data.get(key);
                nameValuePairs.add(new BasicNameValuePair(key, value));
            }

            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));

            HttpResponse response = httpClient.execute(httpPost);
            HttpEntity entity = response.getEntity();

            is = entity.getContent();

            if(response.getStatusLine().getStatusCode() != 200 || is == null) {
                String code = Integer.toString(response.getStatusLine().getStatusCode());
                map.put("msg", "Connection error");
                return map;
            }

        } catch (IOException e) {
            e.printStackTrace();
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
        } catch (Exception e) {
            map.put("msg", "Connection error");
            return map;
        }

        try {
            JSONObject jArray = new JSONObject(result);
            if(!jArray.has("status")) {
                map.put("msg", "An error occured");
                return map;
            }

            map.put("status", jArray.get("status").toString());
            if(jArray.has("msg")) {
                map.put("msg", jArray.get("msg").toString());
            }
            else {
                map.put("msg", "");
            }
        } catch (JSONException e) {
            e.printStackTrace();
            return map;
        }

        return map;
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

