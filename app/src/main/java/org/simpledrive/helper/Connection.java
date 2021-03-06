package org.simpledrive.helper;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.simpledrive.authenticator.CustomAuthenticator;

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
import java.lang.ref.WeakReference;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class Connection {
    // General
    private static WeakReference<Context> ref;
    private final String boundary = "===" + System.currentTimeMillis() + "===";
    private final String LINE_FEED = "\r\n";

    // Connection
    private HttpURLConnection httpConn;
    private OutputStream outputStream;
    private PrintWriter writer;
    private HashMap<String, String> formFields;
    private ArrayList<File> fileParts;
    private static final int timeout = 10000;

    // Reconnect/Replay
    private String endpoint;
    private String action;
    private int tryCount = 0;
    private int maxTryCounts = 1;

    // Progress
    private ProgressListener pListener;
    private long bytesTransferred;
    private long total;

    private static String fingerprint = "";

    private String downloadPath;
    private String downloadFilename;

    public Connection(String endpoint, String action, boolean retry, int retries) {
        this(CustomAuthenticator.getServer(), endpoint, action, timeout, retries);
    }

    public Connection(String endpoint, String action) {
        this(CustomAuthenticator.getServer(), endpoint, action, timeout, 0);
    }

    public Connection(String endpoint, String action, int timeout) {
        this(CustomAuthenticator.getServer(), endpoint, action, timeout, 0);
    }

    public Connection(String server, String endpoint, String action) {
        this(server, endpoint, action, timeout, 0);
    }

    /**
     * Constructor; initialize a new HTTP POST request
     * @param endpoint The endpoint to connect to
     * @param action The action to execute
     */
    public Connection(String server, String endpoint, String action, int timeout, int tryCount) {
        try {
            this.tryCount = tryCount;

            URL url = new URL(server + "api/" + endpoint + "/" + action);
            trustCertificate(server);
            httpConn = (server.startsWith("https")) ? (HttpsURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection();
            httpConn.setUseCaches(false);
            httpConn.setDoOutput(true);
            httpConn.setDoInput(true);
            httpConn.setReadTimeout(timeout);
            httpConn.setConnectTimeout(timeout);
            httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            httpConn.setRequestProperty("Cookie", fingerprint);

            this.endpoint = endpoint;
            this.action = action;
            outputStream = httpConn.getOutputStream();
            writer = new PrintWriter(new OutputStreamWriter(outputStream, "UTF-8"), true);
            formFields = new HashMap<>();
            fileParts = new ArrayList<>();
        } catch (NoSuchAlgorithmException | IOException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set the context for accessing SharedPrefs
     * @param context Activity context
     */
    public static void init(Context context) {
        ref = new WeakReference<>(context);
        if (ref.get() != null) {
            Context ctx = ref.get();
            fingerprint = Preferences.getInstance(ctx).read(Preferences.TAG_FINGERPRINT, "");
        }
    }

    interface ProgressListener {
        void transferred(Integer num);
    }

    /**
     * Set progress listener
     * @param listener Progress listener
     */
    void setListener(final ProgressListener listener) {
        this.pListener = listener;
    }

    void setDownloadPath(String path, String filename) {
        downloadPath = path;
        downloadFilename = filename;
    }

    /**
     * Add a form field to the request
     * @param name  Field name
     * @param value Field value
     */
    public void addFormField(String name, String value) {
        if (writer != null) {
            formFields.put(name, value);
            writer.append(LINE_FEED).append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"").append(name).append("\""); //.append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=UTF-8").append(LINE_FEED);
            writer.append(LINE_FEED);
            writer.append(value);

            writer.flush();
        }
    }

    /**
     * Adds a upload file section to the request
     * @param uploadFile File to be uploaded
     */
    void addFilePart(File uploadFile) {
        try {
            fileParts.add(uploadFile);
            String fileName = uploadFile.getName();
            writer.append(LINE_FEED).append("--").append(boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"0\"; filename=\"").append(fileName).append("\"").append(LINE_FEED);
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
                if (percent % 5 == 0 && pListener != null) {
                    pListener.transferred(percent);
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

    private boolean renewToken() {
        if (tryCount >= maxTryCounts) {
            return false;
        }

        tryCount++;
        Connection con = new Connection("core", "login");
        con.addFormField("user", CustomAuthenticator.getUsername());
        con.addFormField("pass", CustomAuthenticator.getPassword());

        Response res = con.finish();

        if (res.successful()) {
            CustomAuthenticator.setToken(res.getMessage());
        }

        return res.successful();
    }

    /**
     * Executes the same request again
     * @return Replay response
     */
    private Response retry() {
        Connection con = new Connection(endpoint, action, true, tryCount);
        // Add form-fields
        for (HashMap.Entry<String, String> entry : formFields.entrySet()) {
            con.addFormField(entry.getKey(), entry.getValue());
        }
        // Add file-parts
        for (File file : fileParts) {
            con.addFilePart(file);
        }

        return con.finish();
    }

    /**
     * Complete the request and receive response from the server
     * @return Response
     */
    public Response finish() {
        addFormField("token", CustomAuthenticator.getToken());

        try {
            if (writer != null) {
                writer.append(LINE_FEED).flush();
                writer.append("--").append(boundary).append("--").append(LINE_FEED);
                writer.close();
            }

            // Get cookie from header
            List cookieList = httpConn.getHeaderFields().get("Set-Cookie");
            if (cookieList != null && !cookieList.isEmpty()) {
                extractCookies(cookieList);
            }

            // Get status code
            int status = httpConn.getResponseCode();

            if (status == HttpURLConnection.HTTP_FORBIDDEN && renewToken()) {
                return retry();
            }

            // Handle download
            if (downloadPath != null && status == HttpURLConnection.HTTP_OK) {
                // Can't access directory
                if (!new File(downloadPath).canRead()) {
                    httpConn.disconnect();
                    return new Response(false, status, "Could not access download folder");
                }

                InputStream is = httpConn.getInputStream();

                // Retrieve filename from response header
                String header = httpConn.getHeaderField("Content-Disposition");

                if (header != null && downloadFilename == null) {
                    downloadFilename = header.substring(header.lastIndexOf("filename=") + 9);
                    downloadFilename = URLDecoder.decode(downloadFilename, "UTF-8");
                }

                if (downloadFilename == null) {
                    is.close();
                    httpConn.disconnect();
                    return new Response(false, status, "Empty filename");
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
                    if (percent % 5 == 0 && pListener != null) {
                        pListener.transferred(percent);
                    }
                }

                // Cleanup
                outputStream.close();
                is.close();
                httpConn.disconnect();

                return new Response(true, status, saveFilePath);
            }
            // Receive answer from server
            else {
                boolean success = status == HttpURLConnection.HTTP_OK;
                InputStream is = (success) ? httpConn.getInputStream() : httpConn.getErrorStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }

                String result = sb.toString();
                JSONObject obj = new JSONObject(result);

                // Cleanup
                reader.close();
                is.close();
                httpConn.disconnect();

                return new Response(success, status, obj.getString("msg"));
            }
        } catch (Exception e) {
            e.printStackTrace();

            int status = 400;
            String message = "Unknown error";
            try {
                status = httpConn.getResponseCode();
                message = httpConn.getResponseMessage();
            } catch (IOException e1) {
                e1.printStackTrace();
            }

            httpConn.disconnect();

            return new Response(false, status, message);
        }
    }

    private void extractCookies(List cookieList) {
        for (int i = 0; i < cookieList.size(); i++) {
            HttpCookie cookie = HttpCookie.parse(cookieList.get(i).toString()).get(0);

            // Only override fingerprint if none is set
            if (cookie.getName().equals("fingerprint") && fingerprint.equals("") && ref != null && ref.get() != null) {
                fingerprint = cookie.toString();
                Context ctx = ref.get();
                Preferences.getInstance(ctx).write(Preferences.TAG_FINGERPRINT, fingerprint);
            }
        }
    }

    private void trustCertificate(final String server) throws KeyManagementException, NoSuchAlgorithmException {
        System.setProperty("https.protocols", "TLS");

        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                        System.out.println ( "checkClientTrusted()" );
                    }
                    @Override
                    public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                        System.out.println ( "checkServerTrusted()" );

                    }
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        System.out.println ( "X509Certificate()" );
                        return new X509Certificate[0];
                    }
                }
        };
        final SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {

            @Override
            public boolean verify(String arg0, SSLSession arg1) {
                String thisServer = "https://" + arg0;
                return (server.startsWith(thisServer) && !server.startsWith(thisServer + "."));
            }
        });
    }

    public static void logout() {
        // Do something
    }

    public class Response {
        private boolean success;
        private int status;
        private String msg;

        public Response(boolean success, int status, String message) {
            this.success = success;
            this.status = status;
            this.msg = message;
        }

        public boolean successful() {
            return this.success;
        }

        public int getStatus() { return this.status; }

        public String getMessage() {
            return this.msg;
        }
    }
}