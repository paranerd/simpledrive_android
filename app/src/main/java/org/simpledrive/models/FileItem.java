package org.simpledrive.models;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;

public class FileItem
{
	private String id;
	private String filename;
	private String size;
	private String edit;
	private String path;
	private Drawable icon;
	private Bitmap thumb;
	private String type;
	private String owner;
	private Integer scrollPos = 0;
	private Integer shareStatus;

	// Used for hierarchy elements
	public FileItem(String id, String filename, String path) {
		this(id, filename, path, "", "", "folder", "", 0);
	}

	// For all other elements
	public FileItem(String id, String filename, String path, String size, String edit, String type, String owner, Integer sharestatus)
    {
		this.id = id;
		this.filename = filename;
		this.size = size;
		this.edit = edit;
		this.path = path;
		this.type = type;
		this.owner = owner;
		this.shareStatus = sharestatus;
	}

	public boolean is(String type)
    {
		return this.type.equals(type);
	}

	public String getID()
	{
		return this.id;
	}
	
	public String getFilename()
    {
		return this.filename;
	}
	
	public String getSize()
    {
		return this.size;
	}

	public String getPath()
    {
		return this.path;
	}

	public Drawable getIcon() {
		return this.icon;
	}

	public void setIcon(Drawable icon) {
		this.icon = icon;
	}

	public Bitmap getThumb() {
		return this.thumb;
	}

	public void setThumb(Bitmap thumb) {
		this.thumb = thumb;
	}
	
	public String getType() {
		return this.type;
	}

	public String getOwner() {
		return this.owner;
	}

	public Integer getShareStatus() {
		return this.shareStatus;
	}

	public void setScrollPos(int pos)
    {
		this.scrollPos = pos;
	}

	public int getScrollPos()
    {
		return this.scrollPos;
	}

	public String getEdit() {
		return this.edit;
	}
}
