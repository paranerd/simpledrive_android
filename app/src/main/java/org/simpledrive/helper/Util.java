package org.simpledrive.helper;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Movie;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;

import org.simpledrive.R;
import org.simpledrive.models.FileItem;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import static android.content.Context.MODE_PRIVATE;

public class Util {
    public static Bitmap getThumb(String file, int size) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file, o);

        int width_tmp = o.outWidth;
        int height_tmp = o.outHeight;
        float scale = 1;

        // Used to scale with power of 2, but filesize will be 3x as big
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
        float size = Long.parseLong(sSize);
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

    public static long stringToByte(String s) {
        if (s.length() == 0) {
            return 0;
        }

        String dim = (s.length() > 2) ? s.substring(s.length() - 2).toUpperCase() : s.toUpperCase();
        s = s.replaceAll("\\D+","");

        if (s.length() == 0) {
            return 0;
        }

        long size = Long.parseLong(s);

        switch (dim) {
            case "KB":
                return size * 1024;
            case "MB":
                return size * 1024 * 1024;
            case "GB":
                return size * 1024 * 1024;
            default:
                return size;
        }
    }

    public static ArrayList<FileItem> sortFilesByName(ArrayList<FileItem> list, final int sortOrder) {
        Collections.sort(list, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem item1, FileItem item2) {
                if(item1.is("folder") && !item2.is("folder")) {
                    return -1;
                }
                if(!item1.is("folder") && item2.is("folder")) {
                    return 1;
                }
                return sortOrder * (item1.getFilename().toLowerCase().compareTo(item2.getFilename().toLowerCase()));
            }
        });
        return list;
    }

    public static int getMax(int a, int b) {
        return (a > b) ? a : b;
    }

    public static int getMin(int a, int b) {
        return (a < b) ? a : b;
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
            return "";
        }
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

    public static long folderSize(String path) {
        File directory = new File(path);
        long length = 0;

        for (File file : directory.listFiles()) {
            if (file.isFile())
                length += file.length();
            else
                length += folderSize(file.getAbsolutePath());
        }
        return length;
    }

    public static long getTimestamp() {
        return System.currentTimeMillis();
    }

    public static int[] scaleImage(AppCompatActivity ctx, int img_width, int img_height) {
        int[] dim = new int[2];
        DisplayMetrics displaymetrics = new DisplayMetrics();
        ctx.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

        float screen_width = displaymetrics.widthPixels;
        float screen_height = displaymetrics.heightPixels;
        float shrink_to = (img_height > screen_height || img_width > screen_width) ? Math.min(screen_height, screen_width / img_width) : 1;

        dim[0] = Math.round(img_width * shrink_to);
        dim[1] = Math.round(img_height * shrink_to);

        return dim;
    }

    public static boolean writeToFile(String filename, String data, Context context) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(filename, MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
            return true;
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
        return false;
    }

    public static String readFromFile(String filename, Context context) {
        String ret = "";

        try {
            InputStream inputStream = context.openFileInput(filename);

            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString = "";
                StringBuilder stringBuilder = new StringBuilder();

                while ((receiveString = bufferedReader.readLine()) != null) {
                    stringBuilder.append(receiveString);
                }

                inputStream.close();
                ret = stringBuilder.toString();
            }
        } catch (IOException e) {
            // An error happened
        }

        return ret;
    }

    public static boolean isGIF(String path) {
        try {
            URL url = new URL("file://" + path);
            URLConnection con = url.openConnection();
            InputStream is = con.getInputStream();
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int len = 0;

            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }

            is.close();
            byte[] bytes = os.toByteArray();

            Movie gif = Movie.decodeByteArray(bytes, 0, bytes.length);

            return (gif != null);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String getCacheDir() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/simpleDrive/";
    }

    public static int getThumbSize(AppCompatActivity ctx, int layout) {
        DisplayMetrics displaymetrics = new DisplayMetrics();
        ctx.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

        return (layout == R.layout.listview_detail) ? Util.dpToPx(100) : displaymetrics.widthPixels / 3;
    }
}
