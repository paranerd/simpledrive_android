package simpledrive.lib;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class Helper {
    public static Bitmap getThumb(String file, int size) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file, o);

        int width_tmp = o.outWidth;
        int height_tmp = o.outHeight;
        float scale = 1;

        // Used to scale with power of 2, but filesize is 3x as big
        /*int ratio = Math.min(o.outWidth/size, o.outHeight / size);
        int sampleSize = Integer.highestOneBit((int) Math.floor(ratio));
        sampleSize = (sampleSize == 0) ? 1 : sampleSize;*/

        if (height_tmp > size || width_tmp > size) {
            scale = 1 / Math.min((float) size / height_tmp, (float) size / width_tmp);
        }

        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = Math.round(scale);
        return BitmapFactory.decodeFile(file, o2);
    }

    public static String convertSize(String sSize) {
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

    public static ArrayList<Item> sort(ArrayList<Item> list) {
        Collections.sort(list, new Comparator<Item>() {
            @Override
            public int compare(Item o1, Item o2) {
                return o1.getFilename().toLowerCase().compareTo(o2.getFilename().toLowerCase());
            }
        });
        return list;
    }

    public static String md5(String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = java.security.MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte messageDigest[] = digest.digest();

            // Create Hex String
            StringBuilder hexString = new StringBuilder();
            for (byte aMessageDigest : messageDigest) {
                hexString.append(Integer.toHexString(0xFF & aMessageDigest));
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static int dpToPx(int dp) {
        DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }

    public static int pxToDp(int px) {
        DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
        float logicalDensity = displayMetrics.density;

        return (int) Math.ceil(px / logicalDensity);
    }


}
