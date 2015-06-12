package simpledrive.library;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import android.os.Environment;
import android.util.Log;

public class DownloadFile {

	private static DownloadListener listener;
	
	public DownloadFile(final DownloadListener list)
	{
		super();
		listener = list;
	}
	
	public static interface DownloadListener
	{
		void transferred(Integer num);
	}
	
	public static String download(String URL, HashMap<String, String> data, String filename, String target) {
		try {
		DefaultHttpClient httpClient = Connection.getThreadSafeClient();
		HttpPost httpPost = new HttpPost(URL);
		
		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
		Iterator<String> myVeryOwnIterator = data.keySet().iterator();
		while(myVeryOwnIterator.hasNext()) {
		    String key=(String)myVeryOwnIterator.next();
		    String value=(String)data.get(key);
		    nameValuePairs.add(new BasicNameValuePair(key, value));
		}

		httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		
		// Execute HTTP post request
		HttpResponse response = httpClient.execute(httpPost);
		HttpEntity resEntity = response.getEntity();

		if (resEntity != null) {
            InputStream in = resEntity.getContent();
            File path = new File(target);

            path.mkdirs();
            File file = new File(path, filename);
            FileOutputStream fos = new FileOutputStream(file);

            byte[] buffer = new byte[1024];
            int len1 = 0;
            int loaded = 0;
            while ((len1 = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, len1);
                    loaded += len1;
                    listener.transferred((int) ((loaded / (float) resEntity.getContentLength()) * 100));
            }

            fos.close();
            return "Success";
		}
		}
		catch (ClientProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "Fail";
	}
}
