package org.simpledrive.helper;

import android.graphics.Bitmap;

public class FileItem {
	private String id;
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
	private String imgPath = null;
	private Integer scrollPos = 0;
	private boolean selfshared;
	private boolean shared;

	// Used for the root element
	public FileItem(String id, String filename, String path, Bitmap icon) {
		this(id, filename, "", path, "", "", "folder", "", false, false, icon, null, "", "");
	}

	// For all other elements
	public FileItem(String id, String filename, String parent, String path, String size, String edit, String type, String owner, boolean selfshared, boolean shared, Bitmap icon, Bitmap thumb, String thumbPath, String imgPath) {
		this.id = id;
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
		this.selfshared = selfshared;
		this.shared = shared;
		this.thumbPath = thumbPath;
		this.imgPath = imgPath;
	}

	public boolean is(String type) {
		return this.type.equals(type);
	}

	public String getID() {
		return this.id;
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

	public String getThumbPath() {
		return this.thumbPath;
	}

	public String getImgPath() {
		return this.imgPath;
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

	public boolean selfshared() {
		return this.selfshared;
	}

	public boolean shared() {
		return this.shared;
	}

	public void setScrollPos(int pos) {
		this.scrollPos = pos;
	}

	public int getScrollPos() {
		return this.scrollPos;
	}
}
