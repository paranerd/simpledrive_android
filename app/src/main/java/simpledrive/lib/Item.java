package simpledrive.lib;

import android.graphics.Bitmap;

public class Item {
	private String filename;
	private String data;
	private String date;
	private String path;
	private Bitmap img;
	private String type;
	
	public Item(String filename, String data, String date, String path, Bitmap img, String type) {
		this.filename = filename;
		this.data = data;
		this.date = date;
		this.path = path;
		this.img = img;
		this.type = type;
	}
	
	public String getFilename() {
		return this.filename;
	}
	
	public String getData() {
		return this.data;
	}
	
	public String getDate() {
		return this.date;
	}
	
	public String getPath() {
		return this.path;
	}
	
	public Bitmap getImg() {
		return this.img;
	}
	
	public String getType() {
		return this.type;
	}
	
	public void setImg(Bitmap img) {
		this.img = img;
	}
}
