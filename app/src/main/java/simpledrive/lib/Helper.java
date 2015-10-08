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

    public static Bitmap shrinkBitmap(Bitmap bmp) {
        int imgHeight = bmp.getHeight();
        int imgWidth = bmp.getWidth();

        DisplayMetrics displaymetrics = Resources.getSystem().getDisplayMetrics();
        int screenHeight = displaymetrics.heightPixels;
        int screenWidth = displaymetrics.widthPixels;

        int newWidth, newHeight;
        float shrinkTo;

        if (imgHeight > screenHeight || imgWidth > screenWidth) {
            shrinkTo = Math.min((float) screenHeight / imgHeight, (float) screenWidth / imgWidth);
            newWidth = (int) (imgWidth * shrinkTo);
            newHeight = (int) (imgHeight * shrinkTo);
        } else {
            newWidth = imgWidth;
            newHeight = imgHeight;
        }
        return Bitmap.createScaledBitmap(bmp, newWidth, newHeight, false);
    }

    public static Bitmap shrink(String file, int width, int height){
        BitmapFactory.Options bitopt = new BitmapFactory.Options();
        bitopt.inJustDecodeBounds = true;

        int h = (int) Math.ceil(bitopt.outHeight / (float)height);
        int w = (int) Math.ceil(bitopt.outWidth / (float)width);

        if(h > 1 || w > 1){
            if(h > w){
                bitopt.inSampleSize=h;

            }
            else{
                bitopt.inSampleSize=w;
            }
        }
        bitopt.inJustDecodeBounds = false;

        return BitmapFactory.decodeFile(file, bitopt);
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
            StringBuffer hexString = new StringBuffer();
            for (int i=0; i<messageDigest.length; i++)
                hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static int dpToPx(int dp) {
        DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
        int px = Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return px;
    }


}
