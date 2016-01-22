package simpledrive.lib;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Connection {
    private final String boundary;
    private final String LINE_FEED = "\r\n";

    private HttpURLConnection httpConn;
    private OutputStream outputStream;
    private PrintWriter writer;

    public long bytesTransferred;
    public long total;

    private final ProgressListener listener;

    public static String token;
    private static String server;

    public static String cookie;
    private boolean forceCookie = false;

    private String downloadPath;
    private String downloadFilename;

    /**
     * This constructor initializes a new HTTP POST request
     *
     * @param api The api to connect to
     */
    public Connection(String api, final ProgressListener listener) {
        boundary = "===" + System.currentTimeMillis() + "===";
        this.listener = listener;

        try {
            URL url = new URL(server + "api/" + api + ".php");
            trustCertificate();
            httpConn = (server.startsWith("https")) ? (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
            httpConn.setUseCaches(false);
            httpConn.setDoOutput(true);
            httpConn.setDoInput(true);
            httpConn.setReadTimeout(10000);
            httpConn.setConnectTimeout(10000);
            httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            httpConn.setRequestProperty("Cookie", cookie);

            outputStream = httpConn.getOutputStream();
            writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);
        } catch (NoSuchAlgorithmException | IOException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a form field to the request
     *
     * @param name  field name
     * @param value field value
     */
    public void addFormField(String name, String value) {
        if(writer != null) {
            writer.append(LINE_FEED).append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"").append(name).append("\"").append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
            writer.append(LINE_FEED);
            writer.append(value); //.append(LINE_FEED);
            writer.flush();
        }
    }

    public void forceSetCookie() {
        forceCookie = true;
    }

    public interface ProgressListener
    {
        void transferred(Integer num);
    }

    public void setDownloadPath(String path, String filename) {
        downloadPath = path;
        downloadFilename = filename;
    }

    /**
     * Adds a upload file section to the request
     *
     * @param fieldName  name attribute in <input type="file" name="..." />
     * @param uploadFile a File to be uploaded
     */
    public void addFilePart(String fieldName, File uploadFile) {
        try {
            String fileName = uploadFile.getName();
            writer.append(LINE_FEED).append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"").append(fieldName).append("\"; filename=\"").append(fileName).append("\"").append(LINE_FEED);
            writer.append("Content-Type: ").append(URLConnection.guessContentTypeFromName(fileName)).append(LINE_FEED);
            writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
            writer.append(LINE_FEED);
            writer.flush();

            total = uploadFile.length();

            FileInputStream inputStream = new FileInputStream(uploadFile);
            byte[] buffer = new byte[4096];

            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                bytesTransferred += bytesRead;
                int percent = (int) ((bytesTransferred / (float) total) * 100);
                if(percent % 5 == 0 && listener != null) {
                    listener.transferred(percent);
                }
            }
            outputStream.flush();
            inputStream.close();

            writer.append(LINE_FEED);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Completes the request and receives response from the server.
     *
     * @return a list of Strings as response in case the server returned
     * status OK, otherwise an exception is thrown.
     */
    public HashMap<String, String> finish() {
        String status;
        String msg;

        addFormField("token", token);

        try {
            if(writer != null) {
                writer.append(LINE_FEED).flush();
                writer.append("--").append(boundary).append("--").append(LINE_FEED);
                writer.close();
            }

            // Get cookie from header
            List cookieList = httpConn.getHeaderFields().get("Set-Cookie");
            if (cookieList != null && (cookie == null || forceCookie)) {
                cookie = cookieList.get(0).toString();
            }

            if (httpConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                // Open input stream from the HTTP connection
                InputStream is = httpConn.getInputStream();

                // Is download pending?
                if(downloadPath != null) {
                    // Retrieve filename from response header
                    String header = httpConn.getHeaderField("Content-Disposition");

                    if (header != null && downloadFilename == null) {
                        downloadFilename = header.substring(header.lastIndexOf("filename=") + 9);
                        downloadFilename = URLDecoder.decode(downloadFilename, "UTF-8");
                    }

                    if(downloadFilename == null) {
                        HashMap<String, String> map = new HashMap<>();
                        map.put("status", "error");
                        map.put("msg", "Empty filename");
                        return map;
                    }

                    String saveFilePath = downloadPath + File.separator + downloadFilename;

                    // Open an output stream to save into file
                    FileOutputStream outputStream = new FileOutputStream(saveFilePath);

                    total = httpConn.getContentLength();

                    int bytesRead;
                    byte[] buffer = new byte[4096];
                    while ((bytesRead = is.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        bytesTransferred += bytesRead;
                        int percent = (int) ((bytesTransferred / (float) total) * 100);
                        if(percent % 5 == 0 && listener != null) {
                            listener.transferred(percent);
                        }
                    }

                    outputStream.close();
                    status = "ok";
                    msg = "ok";
                }
                // Receive answer from server
                else {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;

                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }

                    reader.close();
                    String result = sb.toString();

                    JSONObject obj = new JSONObject(result);
                    status = "ok";
                    msg = obj.getString("msg");
                }

                // Cleanup
                is.close();
                httpConn.disconnect();
            }
            else {
                status = "error";
                msg = (httpConn.getResponseCode() == 404) ? "Connection error" : httpConn.getResponseMessage();
            }
        } catch (Exception e) {
            e.printStackTrace();
            status = "error";
            msg = "Connection error";
        }

        HashMap<String, String> map = new HashMap<>();
        map.put("status", status);
        map.put("msg", msg);
        return map;
    }

    public void trustCertificate () throws KeyManagementException, NoSuchAlgorithmException {
        System.setProperty("https.protocols", "SSLv3");

        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] arg0,
                                                   String arg1) throws CertificateException {
                        System.out.println ( "checkClientTrusted()" );
                    }
                    @Override
                    public void checkServerTrusted(X509Certificate[] arg0,
                                                   String arg1) throws CertificateException {
                        System.out.println ( "checkServerTrusted()" );

                    }
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        System.out.println ( "X509Certificate()" );
                        return new X509Certificate[0];
                    }
                }
        };
        final SSLContext sc = SSLContext.getInstance("SSLv3");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {

            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                return true;
            }
        });
    }

    public static void setToken(String t) {
        token = t;
    }

    public static void setServer(String s) {
        server = s;
    }

    public static String getServer() {
        return server;
    }

    public static void logout(Context ctx) {
        cookie = null;
        AccountManager am = AccountManager.get(ctx);
        Account aaccount[] = am.getAccounts();
        for (Account anAaccount : aaccount) {
            if (anAaccount.type.equals("org.simpledrive")) {
                am.removeAccount(new Account(anAaccount.name, anAaccount.type), null, null);
            }
        }
    }
}