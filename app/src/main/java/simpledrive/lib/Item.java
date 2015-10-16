package simpledrive.lib;

import android.graphics.Bitmap;

public class Item {
	private String filename;
	private String data;
	private String date;
	private String path;
	private Bitmap img;
	private String type;
	private boolean thumbLoading = false;
	private boolean selected = false;
	
	public Item(String filename, String data, String date, String path, Bitmap img, String type) {
		this.filename = filename;
		this.data = data;
		this.date = date;
		this.path = path;
		this.img = img;
		this.type = type;
	}

	public boolean is(String type) {
		return this.type.equals(type);
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

	public boolean isThumbLoading() {
		return this.thumbLoading;
	}

	public void setThumbLoading() {
		this.thumbLoading = true;
	}

	public boolean isSelected() {
		return this.selected;
	}

	public void setSelected(boolean value) {
		this.selected = value;
	}

	public void toggleSelection() {
		this.selected = !this.selected;
	}
	
	public String getType() {
		return this.type;
	}
	
	public void setImg(Bitmap img) {
		this.img = img;
	}
}
