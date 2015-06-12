package simpledrive.library;

import java.io.File;
import java.sql.Date;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.simpledrive.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;
import android.webkit.MimeTypeMap;

	public class FileLoader extends AsyncTaskLoader<ArrayList<Item>> {

		static Bitmap img;
		public static String prevDir;
		public static File currentDir;
	    public static ArrayList<Item> directories;
	    public static ArrayList<Item> files;
	    private Context context;
	    private FileAdapter adapter;
	    private static int currentTask = 0;

		public FileLoader(Context context, FileAdapter adapter) {
			super(context);
			this.context = context;
			this.adapter = adapter;
		}

		@Override
		public ArrayList<Item> loadInBackground() {
			currentTask++;
	        
	    	File[]dirs = currentDir.listFiles();
	   	 //this.setTitle(f.getName());
	   	 
	    	prevDir = "";
	   	 directories = new ArrayList<Item>();
	   	 files = new ArrayList<Item>();
	   	 
	   	 try {
	   		 for(File ff: dirs)
	   		 {
	   			Date lastModDate = new Date(ff.lastModified()); 
	   			DateFormat formater = DateFormat.getDateTimeInstance();
	   			String date_modify = formater.format(lastModDate);
	   			if(ff.isDirectory()){
	   				File[] fbuf = ff.listFiles(); 
	   				int buf = (fbuf != null) ? fbuf.length : 0;
	   				
	   				String num_item = String.valueOf(buf);
	   				num_item += (buf == 0) ? " item" : " items";
	   				img = BitmapFactory.decodeResource(context.getResources(), R.drawable.folder_thumb);
	   				
	   				Item item = new Item(ff.getName(), num_item, date_modify, ff.getAbsolutePath(), img, "folder");
	   				directories.add(item);
	   			}
	   			else
	   			{
	   				String type = "file";
	   				img = BitmapFactory.decodeResource(context.getResources(), R.drawable.unknown_thumb);
	   				Long lsize = ff.length();
	   				String size = convertSize(lsize.toString());
	   				String extension = MimeTypeMap.getFileExtensionFromUrl(ff.getAbsolutePath());
	   				if(extension != null) {
	   					MimeTypeMap mime = MimeTypeMap.getSingleton();
	   					String filetype = mime.getMimeTypeFromExtension(extension);
	   					if(filetype != null) {
	   						String[] content = filetype.split("/");
	   						if(content[0].equals("image")) {
	   							type = "image";
	   							img = BitmapFactory.decodeResource(context.getResources(), R.drawable.unknown_thumb);
	   						}
	   					}
	   				}
	   				Item item = new Item(ff.getName(), size, date_modify, ff.getAbsolutePath(), img, type);
	   				files.add(item);
	   			}
	   		 }
	   	 }catch(Exception e)
	   	 {    
	   		 
	   	 }
	   	 	directories = sort(directories);
	   	 	files = sort(files);
	   	 	directories.addAll(files);
			 if(!currentDir.getName().equalsIgnoreCase("")) {
				 prevDir = currentDir.getParent();
			 }
	   	 	getThumbs();
			return directories;
		}
		
		@Override
		public void deliverResult(ArrayList<Item> dirs) {
			if (isReset()) {
				if(dirs != null) {
					onReleaseResources(dirs);
					return;
				}
			}
			ArrayList<Item> oldAlbum = directories;
			directories = dirs;
			
			if(isStarted()) {
				super.deliverResult(dirs);
			}
			
			if(oldAlbum != null && oldAlbum != dirs) {
				onReleaseResources(oldAlbum);
			}
		}
		
		@Override
		public void forceLoad() {
			super.forceLoad();
		}
		
		@Override
		protected void onStartLoading() {
			if(directories != null) {
				deliverResult(directories);
			}
			
			if(takeContentChanged()) {
				forceLoad();
			} else if (directories == null) {
				forceLoad();
			}
		}
		
		@Override
		protected void onStopLoading() {
			cancelLoad();
		}

		@Override
		protected void onReset() {
			onStopLoading();
			
			if(directories != null) {
				onReleaseResources(directories);
				directories = null;
			}
		}
		
		@Override
		public void onCanceled(ArrayList<Item> dir) {
			super.onCanceled(dir);
			onReleaseResources(dir);
		}
		
		protected void onReleaseResources(ArrayList<Item> albumList) {
			// Nothing to do here for a simple List
		}

private static ArrayList<Item> sort(ArrayList<Item> list) {
	Collections.sort(list, new Comparator<Item>() {
		@Override
		public int compare(Item o1, Item o2) {
			return o1.getFilename().toLowerCase().compareTo(o2.getFilename().toLowerCase());
		}
	});
	return list;
}

// Helper
protected static String convertSize(String sSize) {
	float size = Integer.parseInt(sSize);
	String convSize;
	
	if(size > 1073741824) {
		convSize = Math.floor((size / 1073741824) * 100) / 100 + " GB";
	}
	else if(size > 1048576) {
		convSize = Math.floor((size / 1048576) * 100) / 100 + " MB";
	}
	else if(size > 1024) {
		convSize = Math.floor((size / 1024) * 100) / 100 + " KB";
	}
	else {
		convSize = size + " Byte";
	}
	return convSize;
}

static Bitmap Shrinkmethod(String file, int width, int height){
    BitmapFactory.Options bitopt=new BitmapFactory.Options();
    bitopt.inJustDecodeBounds=true;
    Bitmap bit=BitmapFactory.decodeFile(file, bitopt);

    int h=(int) Math.ceil(bitopt.outHeight/(float)height);
    int w=(int) Math.ceil(bitopt.outWidth/(float)width);

    if(h>1 || w>1){
        if(h>w){
            bitopt.inSampleSize=h;

        }else{
            bitopt.inSampleSize=w;
        }
    }
    bitopt.inJustDecodeBounds=false;
    bit=BitmapFactory.decodeFile(file, bitopt);

    return bit;
}

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
private void getThumbs() {
	int index = 0;
	int imgIndex = 0;
	for(Item i : directories) {
		if(i.getType().equals("image")) {
			HashMap<String, Object> hm = new HashMap<String, Object>();
			hm.put("path", i.getPath());
			hm.put("pos", index);
			hm.put("current", currentTask);
			Thumb thmb = new Thumb();
		    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && imgIndex < 100) {
		        thmb.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, hm);
		      }
		      else {
		        thmb.execute(hm);
		      }
		    imgIndex++;
		}
		index++;
	}
}

private class Thumb extends AsyncTask<HashMap<String, Object>, Bitmap, HashMap<String, Object>> {
	  
 	@Override
     protected void onPreExecute() {
         super.onPreExecute();
 	}
 	
 	@Override
     protected HashMap<String, Object> doInBackground(HashMap<String, Object>... hm) {
 		Integer task = (Integer) hm[0].get("current");
 		if(task != currentTask) {
 			cancel(true);
 		}
 		if(isCancelled()) {
 			return null;
 		}
 		String path = (String) hm[0].get("path");
 		int position = (Integer) hm[0].get("pos");
 		
 		HashMap<String, Object> hmBitmap = new HashMap<String, Object>();
 		
 		Bitmap img = Shrinkmethod(path, 50, 50);
 		hmBitmap.put("img", img);
 		hmBitmap.put("position", position);
 		hmBitmap.put("task", task);
 		return hmBitmap;
 	}
 	 @Override
      protected void onPostExecute(HashMap<String, Object> result) {
 		 int pos = (Integer) result.get("position");
 		 Bitmap img2 = (Bitmap) result.get("img");
 		 int task = (Integer) result.get("task");
 		 
 		if(task == currentTask && directories != null && adapter != null) {
 		 directories.get(pos).setImg(img2);
 		 adapter.notifyDataSetChanged();
 		}
 	 }
 }
}