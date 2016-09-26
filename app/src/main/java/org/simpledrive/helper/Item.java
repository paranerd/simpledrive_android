package org.simpledrive.helper;

import android.graphics.Bitmap;

import org.json.JSONObject;

public class Item {
	private JSONObject json;
	private String filename;
	private String parent;
	private String size;
	private String edit;
	private String path;
	private Bitmap icon;
	private Bitmap thumb;
	private String type;
	private String owner;
	private String thumbPath = null;
	private Integer scrollPos = 0;

	// Used for the root element
	public Item(JSONObject json, String filename, String path) {
		this.json = json;
		this.filename = filename;
		this.path = path;
	}

	// For all other elements
	public Item(JSONObject json, String filename, String parent, String path, String size, String edit, String type, String owner, Bitmap icon, Bitmap thumb) {
		this.json = json;
		this.filename = filename;
		this.parent = parent;
		this.size = size;
		this.edit = edit;
		this.path = path;
		this.icon = icon;
		this.thumb = thumb;
		this.type = type;
		this.type = type;
		this.owner = owner;
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

	public Bitmap getIcon() {
		return this.icon;
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
	
	public String getType() {
		return this.type;
	}

	public void setThumb(Bitmap img) {
		this.thumb = img;
	}

	public String getOwner() {
		return this.owner;
	}

	public void setScrollPos(int pos) {
		this.scrollPos = pos;
	}

	public int getScrollPos() {
		return this.scrollPos;
	}
}
