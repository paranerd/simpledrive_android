package simpledrive.lib;

import android.util.Log;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.HttpClientBuilder;

public class Upload implements HttpEntity {
    			
    			private final ProgressListener listener;
    			
    			static HttpEntity yourEntity;
    			static long totalSize;
    			
    			public Upload(final ProgressListener listener)
    			{
    				super();
    				this.listener = listener;
    			}
    			
    			public static HashMap<String, String> upload(HttpEntity theEntity, String url, String path, String rel_path, String currDir, String token) {
                    Log.i("upload", "called");
                    CloseableHttpClient httpClient = Connection.getThreadSafeClient();
                    HashMap<String, String> map = new HashMap<>();

                    if(httpClient == null) {
                        map.put("status", "error");
                        map.put("msg", "Connection error");
                        return map;
                    }

        			HttpPost httpPost = new HttpPost(url);

        			File file = new File(path);
        			ContentBody fileBody = new FileBody(file);
        			
        			MultipartEntityBuilder reqEntity = MultipartEntityBuilder.create();
        			reqEntity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        			reqEntity.addPart("0", fileBody);
        			reqEntity.addTextBody("target", currDir);
                    reqEntity.addTextBody("action", "upload");
                    reqEntity.addTextBody("token", token);
                    reqEntity.addTextBody("paths", rel_path);
        			yourEntity = reqEntity.build();
        			totalSize = yourEntity.getContentLength();
        			
                    httpPost.setEntity(theEntity);

                    Log.i("before", "execute");
                    try {
        				HttpResponse response = httpClient.execute(httpPost);
                        Log.i("after", "execute");

                        Log.i("code", Integer.toString(response.getStatusLine().getStatusCode()));

                        if(response.getStatusLine().getStatusCode() != 200) {
                            map.put("status", "error");
                            map.put("msg", response.getStatusLine().getReasonPhrase());
                            Log.i("error", "1");
                            //httpClient.close();
                            return map;
                        }
                        map.put("status", "ok");
                        map.put("msg", "");
                        Log.i("status", "ok");
                        //httpClient.close();
                        return map;
        			} catch (IOException e) {
        				e.printStackTrace();
                        map.put("status", "error");
                        map.put("msg", "Connection error");
                        Log.i("error", "Connection error");
                        return map;
                    }
    			}
    			
    			public interface ProgressListener
    			{
    				void transferred(Integer num);
    			}
    			
                @Override
                public void consumeContent() throws IOException {
                    yourEntity.consumeContent();
                }
                @Override
                public InputStream getContent() throws IOException,
                        IllegalStateException {
                    return yourEntity.getContent();
                }
                @Override
                public Header getContentEncoding() {
                    return yourEntity.getContentEncoding();
                }
                @Override
                public long getContentLength() {
                    return yourEntity.getContentLength();
                }
                @Override
                public Header getContentType() {
                    return yourEntity.getContentType();
                }
                @Override
                public boolean isChunked() {
                    return yourEntity.isChunked();
                }
                @Override
                public boolean isRepeatable() {
                    return yourEntity.isRepeatable();
                }
                @Override
                public boolean isStreaming() {
                    return yourEntity.isStreaming();
                } // CONSIDER put a _real_ delegator into here!

                @Override
                public void writeTo(OutputStream outstream) throws IOException {

                    class ProxyOutputStream extends FilterOutputStream {
                		private long transferred;
                		
                        public ProxyOutputStream(OutputStream proxy) {
                            super(proxy);
                			this.transferred = 0;
                        }
                        public void write(int idx) throws IOException {
                            out.write(idx);
                			this.transferred++;
                			listener.transferred((int) ((this.transferred / (float) totalSize) * 100));
                        }
                        public void write(byte[] bts) throws IOException {
                            out.write(bts);
                        }

                        public void write(byte[] bts, int st, int end) throws IOException {
                            out.write(bts, st, end);
                			this.transferred += end;
                			listener.transferred((int) ((this.transferred / (float) totalSize) * 100));
                        }

                        public void flush() throws IOException {
                            Log.i("upload", "flushed");
                            out.flush();
                        }
                        public void close() throws IOException {
                            Log.i("upload", "closed");
                            out.close();
                        }
                    }

                    class ProgressiveOutputStream extends ProxyOutputStream {
                        public ProgressiveOutputStream(OutputStream proxy) {
                            super(proxy);
                        }
                    }

                    yourEntity.writeTo(new ProgressiveOutputStream(outstream));
                }

            }