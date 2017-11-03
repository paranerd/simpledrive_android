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
	private boolean selfshared;
	private boolean shared;

	// Used for hierarchy elements
	public FileItem(String id, String filename, String path) {
		this(id, filename, path, "", "", "folder", "", false, false);
	}

	// For all other elements
	public FileItem(String id, String filename, String path, String size, String edit, String type, String owner, boolean selfshared, boolean shared)
    {
		this.id = id;
		this.filename = filename;
		this.size = size;
		this.edit = edit;
		this.path = path;
		this.type = type;
		this.owner = owner;
		this.selfshared = selfshared;
		this.shared = shared;
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

	public String getCacheName()
	{
		return this.id;
	}

	public String getThumbName()
	{
		return this.id + "_thumb";
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

	public boolean selfshared() {
		return this.selfshared;
	}

	public boolean shared()
    {
		return this.shared;
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
