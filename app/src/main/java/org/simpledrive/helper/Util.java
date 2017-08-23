package org.simpledrive.helper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Movie;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import org.simpledrive.R;
import org.simpledrive.models.FileItem;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import static android.content.Context.CLIPBOARD_SERVICE;
import static android.content.Context.MODE_PRIVATE;

public class Util {
    public static Bitmap getThumb(String file, int size) {
        if (!new File(file).exists()) {
            return null;
        }

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

    public static boolean writeToData(String filename, String data, Context context) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(filename, MODE_PRIVATE));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
            return true;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String readFromData(String filename, Context context) {
        String ret = "";

        try {
            InputStream inputStream = context.openFileInput(filename);

            if (inputStream != null) {
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String receiveString;
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

    public static String readTextFromStorage(String path) {
        // Get the text file
        File file = new File(path);

        // Read text from file
        StringBuilder text = new StringBuilder();

        try {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return text.toString();
    }

    public static byte[] readFromStorage(String path) {
        File file = new File(path);
        int size = (int) file.length();
        byte[] bytes = new byte[size];
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return bytes;
    }

    public static boolean writeTextToStorage(String path, String data) {
        try{
            File file = new File(path);
            FileWriter writer = new FileWriter(file);
            writer.append(data);
            writer.flush();
            writer.close();
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public static boolean writeToStorage(String path, byte[] data) {
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(path);
            fos.write(data, 0, data.length);
            fos.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    public static boolean isGIF(String path) {
        try {
            URL url = new URL("file://" + path);
            URLConnection con = url.openConnection();
            InputStream is = con.getInputStream();
            ByteArrayOutputStream os = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int len;

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

        return (layout == R.layout.listview_detail) ? Util.dpToPx(50) : displaymetrics.widthPixels / 3;
    }

    public static Bitmap getDrawableByName(AppCompatActivity ctx, String name, int def) {
        // Get resource ID
        int drawableResourceId = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
        drawableResourceId = (drawableResourceId != 0) ? drawableResourceId : def;

        // Create bitmap
        return BitmapFactory.decodeResource(ctx.getResources(), drawableResourceId);
    }

    public static Drawable getIconByName(AppCompatActivity ctx, String name, int def) {
        // Get resource ID
        int drawableResourceId = ctx.getResources().getIdentifier("ic_" + name, "drawable", ctx.getPackageName());
        drawableResourceId = (drawableResourceId != 0) ? drawableResourceId : def;

        // Get drawable
        return ContextCompat.getDrawable(ctx, drawableResourceId);
    }

    public static void copyToClipboard(AppCompatActivity ctx, String label, String toast) {
        ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("label", label);
        clipboard.setPrimaryClip(clip);

        if (!toast.equals("")) {
            Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show();
        }
    }

    public static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();

        for (byte b : in) {
            builder.append(String.format("%02x", b));
        }

        return builder.toString();
    }

    public static String byteToString(byte[] arr) {
        try {
            return new String(arr, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String timestampToDate(AppCompatActivity ctx, String timestamp) {
        if (!timestamp.equals("")) {
            long ts = Long.parseLong(timestamp);
            ts = (timestamp.length() > 10) ? ts / 1000 : ts;
            Date date = new Date(ts * 1000L);
            DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM, getCurrentLocale(ctx));
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            return df.format(date);
        }

        return "";
    }

    public static Locale getCurrentLocale(AppCompatActivity ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return ctx.getResources().getConfiguration().getLocales().get(0);
        }
        else {
            return ctx.getResources().getConfiguration().locale;
        }
    }

    // Get Bitmap from an image URL
    public static Bitmap getBitmapFromURL(String strURL) {
        try {
            URL url = new URL(strURL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap myBitmap = BitmapFactory.decodeStream(input);
            return myBitmap;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void showVirtualKeyboard(final Context ctx) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                InputMethodManager m = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);

                if (m != null) {
                    m.toggleSoftInput(0, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 100);
    }
}
