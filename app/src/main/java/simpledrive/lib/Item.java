package simpledrive.lib;

import android.graphics.Bitmap;

import org.json.JSONObject;

public class Item {
	private JSONObject json;
	private String filename;
	private String parent;
	private String size;
	private String edit;
	private String path;
	private Bitmap thumb;
	private String type;
	private String hash;
	private String owner;
	private String thumbPath = null;
	private boolean selected = false;

	public Item(JSONObject json, String filename, String parent, String path, String size, String edit, String type, String owner, String hash, Bitmap thumb) {
	//public Item(String filename, String size, String edit, String path, Bitmap thumb, String type) {
		this.json = json;
		this.filename = filename;
		this.parent = parent;
		this.size = size;
		this.edit = edit;
		this.path = path;
		this.thumb = thumb;
		this.type = type;
		this.hash = hash;
		this.type = type;
		this.owner = owner;
		this.selected = false;
	}

	public boolean is(String type) {
		return this.type.equals(type);
	}

	public JSONObject getJSON() {
		return this.json;
	}
	
	public String getFilename() {
		return this.filename;
	}
	
	public String getSize() {
		return this.size;
	}
	
	public String getLastEdit() {
		return this.edit;
	}
	
	public String getPath() {
		return this.path;
	}

	public String getParent() {
		return this.parent;
	}
	
	public Bitmap getThumb() {
		return this.thumb;
	}

	public void setThumbPath(String path) {
		this.thumbPath = path;
	}

	public String getThumbPath() {
		return this.thumbPath;
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

	public void setThumb(Bitmap img) {
		this.thumb = img;
	}

	public String getHash() {
		return this.hash;
	}

	public String getOwner() {
		return this.owner;
	}
}
