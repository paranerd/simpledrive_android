package simpledrive.lib;

import java.io.File;
import java.sql.Date;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;

import org.simpledrive.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v4.content.AsyncTaskLoader;
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
	   				num_item += (buf == 1) ? " item" : " items";
	   				img = BitmapFactory.decodeResource(context.getResources(), R.drawable.folder_thumb);
	   				
	   				Item item = new Item(ff.getName(), num_item, date_modify, ff.getAbsolutePath(), img, "folder");
	   				directories.add(item);
	   			}
	   			else
	   			{
	   				String type = "file";
	   				img = BitmapFactory.decodeResource(context.getResources(), R.drawable.unknown_thumb);
	   				Long lsize = ff.length();
	   				String size = Helper.convertSize(lsize.toString());
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
	   	 	directories = Helper.sort(directories);
	   	 	files = Helper.sort(files);
	   	 	directories.addAll(files);
			 if(!currentDir.getName().equalsIgnoreCase("")) {
				 prevDir = currentDir.getParent();
			 }
	   	 	//getThumbs();
			return directories;
		}
		
		@Override
		public void deliverResult(ArrayList<Item> dirs)
		{
			if (isReset())
			{
				if(dirs != null)
				{
					onReleaseResources(dirs);
					return;
				}
			}
			ArrayList<Item> oldAlbum = directories;
			directories = dirs;
			
			if(isStarted())
			{
				super.deliverResult(dirs);
			}
			
			if(oldAlbum != null && oldAlbum != dirs)
			{
				onReleaseResources(oldAlbum);
			}
		}
		
		@Override
		public void forceLoad()
		{
			super.forceLoad();
		}
		
		@Override
		protected void onStartLoading()
		{
			if(directories != null)
			{
				deliverResult(directories);
			}
			
			if(takeContentChanged())
			{
				forceLoad();
			} else if (directories == null) {
				forceLoad();
			}
		}
		
		@Override
		protected void onStopLoading()
		{
			cancelLoad();
		}

		@Override
		protected void onReset()
		{
			onStopLoading();
			
			if(directories != null)
			{
				onReleaseResources(directories);
				directories = null;
			}
		}
		
		@Override
		public void onCanceled(ArrayList<Item> dir)
		{
			super.onCanceled(dir);
			onReleaseResources(dir);
		}
		
		protected void onReleaseResources(ArrayList<Item> albumList)
		{
			// Nothing to do here for a simple List
		}

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
private void getThumbs() {
	int index = 0;
	int imgIndex = 0;
	for(Item i : directories) {
		if(imgIndex >= 10) {
			break;
		}
		if(i.getType().equals("image")) {
			HashMap<String, Object> hm = new HashMap<String, Object>();
			hm.put("path", i.getPath());
			hm.put("pos", index);
			hm.put("current", currentTask);
			Thumb thmb = new Thumb();
		    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
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

private class Thumb extends AsyncTask<HashMap<String, Object>, Bitmap, Bitmap>
{
	private int position;
	private int task;

 	@Override
	protected void onPreExecute()
	{
         super.onPreExecute();
 	}
 	
 	@Override
	protected Bitmap doInBackground(HashMap<String, Object>... hm)
	{
 		task = (Integer) hm[0].get("current");
 		if(task != currentTask)
		{
 			cancel(true);
 		}

 		if(isCancelled())
		{
 			return null;
 		}

 		String path = (String) hm[0].get("path");
 		position = (Integer) hm[0].get("pos");

 		return Helper.shrink2(path, 50);
 	}
 	 @Override
      protected void onPostExecute(Bitmap result) {
 		 
 		if(task == currentTask && directories != null && adapter != null) {
 		 directories.get(position).setImg(result);
 		 adapter.notifyDataSetChanged();
 		}
 	 }
 }
}