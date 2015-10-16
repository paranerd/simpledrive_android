package simpledrive.lib;

import java.io.IOException;

import org.simpledrive.R;
import org.simpledrive.RemoteFiles;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

public class AudioService extends Service implements OnCompletionListener, OnPreparedListener, OnErrorListener {

	static private MediaPlayer mediaPlayer;
	private boolean playPause, prepared = false;
	int notificationId = 3;
	
	private AudioBCReceiver receiver;
	
	public static final String PLAY_CHANGED = "org.simpledrive.action.playstatechanged";
	public static final String CHANGE_PLAY = "org.simpledrive.action.changeplay";
	public static final String STOP = "org.simpledrive.action.stop";
	
	@Override
	public void onCreate() {
		super.onCreate();
		IntentFilter filter = new IntentFilter();
		filter.addAction(CHANGE_PLAY);
		filter.addAction(STOP);
	    receiver = new AudioBCReceiver();
	    registerReceiver(receiver, filter);
	    
	    mediaPlayer = new MediaPlayer();
	    mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		mediaPlayer.release();
		unregisterReceiver(receiver);
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		if(playPause) {
			return true;
		}
		stopSelf();
		return true;
	}
	
	@Override
	public void onRebind(Intent intent) {
		super.onRebind(intent);
	}
	
	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		return false;
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return new LocalBinder();
	}

	public class LocalBinder extends Binder {
		
		public AudioService getService() {
			return AudioService.this;
		}
	}
	
	public boolean isPlaying() {
		return playPause;
	}
	
	public void pause(boolean stop) {
		if(mediaPlayer != null && mediaPlayer.isPlaying()) {
			mediaPlayer.pause();
			playPause = false;
			sendBroadcast(PLAY_CHANGED);
			buildNotification(false);
		}
		if(stop) {
			stopForeground(true);
		}
	}
	
	public void play() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            playPause = true;
            sendBroadcast(PLAY_CHANGED);
            buildNotification(true);
        }
	}
	
	public void togglePlay(boolean stop) {
    	if(!prepared) {
    		return;
    	}
        if (!playPause) {
        	play();
        }
        else {
        	pause(stop);
        }
	}
	
	public void initPlay(String url) {
		try {
			mediaPlayer.reset();
			mediaPlayer.setDataSource(url);
		} catch (IllegalArgumentException | IOException | IllegalStateException | SecurityException e) {
			e.printStackTrace();
		}
		mediaPlayer.prepareAsync();
		mediaPlayer.setOnPreparedListener(new OnPreparedListener() {

			@Override
			public void onPrepared(MediaPlayer arg0) {
				// Buffered enough to play
				prepared = true;
				playPause = true;
				mediaPlayer.start();
				sendBroadcast(PLAY_CHANGED);
				buildNotification(true);
			}
		});
	    mediaPlayer.setOnCompletionListener(new OnCompletionListener() {

	        @Override
	        public void onCompletion(MediaPlayer mp) {
	            playPause = false;
	            mediaPlayer.pause();
	            mediaPlayer.seekTo(0);
	            sendBroadcast(STOP);
	        }
	    });
	}
	
	public void sendBroadcast(String what) {
		Intent intent = new Intent();
		intent.setAction(what);
		sendBroadcast(intent);
	}
	
	public void buildNotification(boolean playing) {
        Intent intent = new Intent(this, RemoteFiles.class);
        PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);
        
        Intent change = new Intent(CHANGE_PLAY);
        PendingIntent pChange = PendingIntent.getBroadcast(this, 0, change, 0);
        
        Intent stop = new Intent(STOP);
        PendingIntent pStop = PendingIntent.getBroadcast(this, 0, stop, 0);
        
        RemoteViews remoteView = new RemoteViews(getPackageName(), R.layout.notification);
        remoteView.setImageViewResource(R.id.notifimage, R.drawable.audio_icon2);

        if(mediaPlayer.isPlaying()) {
        	remoteView.setImageViewResource(R.id.notifbutton, R.drawable.pause_icon);
        }
        else {
        	remoteView.setImageViewResource(R.id.notifbutton, R.drawable.play_icon);
        }
        remoteView.setTextViewText(R.id.notiftitle, RemoteFiles.audioFilename);
        remoteView.setOnClickPendingIntent(R.id.notifbutton, pChange);
        remoteView.setOnClickPendingIntent(R.id.notifexit, pStop);

		NotificationManager mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setContent(remoteView)
        	.setContentIntent(pIntent)
        	.setOngoing(false)
        	.setSmallIcon(R.drawable.button_play);

        Notification noti = mBuilder.build();
        
        startForeground(notificationId, noti);
	}
	
	public class AudioBCReceiver extends BroadcastReceiver {
		
		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(CHANGE_PLAY)) {
				togglePlay(false);
			}
			else if(intent.getAction().equals(STOP)) {
				pause(true);
			}
		}
	}
}