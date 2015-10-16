package simpledrive.lib;

import android.graphics.Bitmap;

import org.json.JSONObject;

public class NewItem {
    private JSONObject file;
    private String filename;
    private String parent;
    private String size;
    private String edit;
    private String hash;
    private Bitmap thumb;
    private String type;
    private String owner;
    private boolean selected;

    public NewItem(JSONObject file, String filename, String parent, String size, String edit, String type, String owner, String hash, Bitmap thumb) {
        this.file = file;
        this.filename = filename;
        this.parent = parent;
        this.size = size;
        this.edit = edit;
        this.hash = hash;
        this.thumb = thumb;
        this.type = type;
        this.owner = owner;
        this.selected = false;
    }

    public boolean is(String type) {
        return this.type.equals(type);
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

    public JSONObject getFile() {
        return this.file;
    }

    public String getFilename() {
        return this.filename;
    }

    public String getParent() {
        return this.parent;
    }

    public String getSize() {
        return this.size;
    }

    public String getEdit() {
        return this.edit;
    }

    public String getHash() {
        return this.hash;
    }

    public String getOwner() {
        return this.owner;
    }

    public Bitmap getThumb() {
        return this.thumb;
    }

    public String getType() {
        return this.type;
    }

    public void setThumb(Bitmap img) {
        this.thumb = img;
    }
}
