package simpledrive.library;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

    		public class UploadFile implements HttpEntity {
    			
    			private final ProgressListener listener;
    			
    			static HttpEntity yourEntity;
    			static long totalSize;
    			
    			public UploadFile(final ProgressListener listener)
    			{
    				super();
    				this.listener = listener;
    			}
    			
    			public static String upload(HttpEntity theEntity, String url, String path, String currDir) {
        			DefaultHttpClient httpClient = Connection.getThreadSafeClient();
        			HttpPost httpPost = new HttpPost(url);
        			
        			File file = new File(path);
        			ContentBody fileBody = new FileBody(file);
        			
        			MultipartEntityBuilder reqEntity = MultipartEntityBuilder.create();
        			reqEntity.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        			reqEntity.addPart("uploadedfile", fileBody);
        			reqEntity.addTextBody("dir", currDir.toString());
                    reqEntity.addTextBody("act", "upload");
        			yourEntity = reqEntity.build();
        			totalSize = yourEntity.getContentLength();
        			
                    httpPost.setEntity(theEntity);
                    
                    try {
        				HttpResponse response = httpClient.execute(httpPost);
        				HttpEntity resEntity = response.getEntity();
        				
        				if (resEntity != null) {

                            return EntityUtils.toString(resEntity).trim();
        				}
        			} catch (ClientProtocolException e) {
        				// TODO Auto-generated catch block
        				e.printStackTrace();
        			} catch (IOException e) {
        				// TODO Auto-generated catch block
        				e.printStackTrace();
        			}
    				return "Fail";
    			}
    			
    			public static interface ProgressListener
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
                        /**
                         * @author Stephen Colebourne
                         */
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
                            out.flush();
                        }
                        public void close() throws IOException {
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

            };