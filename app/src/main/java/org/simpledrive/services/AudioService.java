package org.simpledrive.services;

import android.app.Notification;
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

import org.simpledrive.R;
import org.simpledrive.activities.RemoteFiles;
import org.simpledrive.authenticator.CustomAuthenticator;
import org.simpledrive.helper.Preferences;
import org.simpledrive.models.FileItem;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

public class AudioService extends Service {

	private MediaPlayer mediaPlayer;
	private static boolean playPause, prepared = false;
	private final int NOFITICATION_ID = 3;
	public static FileItem currentPlaying;

	private AudioBCReceiver receiver;
	private static boolean active;

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
		if (playPause) {
			return true;
		}
		prepared = false;
		active = false;
		stopSelf();
		return true;
	}

	@Override
	public void onRebind(Intent intent) {
		super.onRebind(intent);
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

	public static boolean isActive() {
		return active;
	}

	public static boolean isPlaying() {
		return prepared && playPause;
	}

	public void seekTo(int pos) {
		int seekTo = (int) (((float)pos / 100) * mediaPlayer.getDuration());
		mediaPlayer.seekTo(seekTo);
	}

	public void pause() {
		if (prepared && mediaPlayer != null && mediaPlayer.isPlaying()) {
			mediaPlayer.pause();
			playPause = false;
			sendBroadcast(PLAY_CHANGED);
			buildNotification();
		}
	}

	public void stop() {
		if (prepared && mediaPlayer != null && mediaPlayer.isPlaying()) {
			mediaPlayer.pause();
			playPause = false;
		}
		active = false;
		stopForeground(true);
	}

	public void play() {
		if (prepared && mediaPlayer != null && !mediaPlayer.isPlaying()) {
			mediaPlayer.start();
			playPause = true;
			sendBroadcast(PLAY_CHANGED);
			buildNotification();
		}
	}

	public void togglePlay() {
		if (!playPause) {
			play();
		}
		else {
			pause();
		}
	}

	public void initPlay(FileItem item) {
		currentPlaying = item;
		active = true;
		prepared = false;

		try {
			// Add fingerprint as cookie
			Map<String, String> headers = new HashMap<>();
			headers.put("Cookie", Preferences.getInstance(getApplicationContext()).read(Preferences.TAG_FINGERPRINT, ""));
			URI uri = new URI(CustomAuthenticator.getServer() + "api/files/get?targets=[" + URLEncoder.encode('"' + item.getID() + '"', "UTF-8") + "]&token=" + CustomAuthenticator.getToken());
			mediaPlayer.reset();
			mediaPlayer.setDataSource(getApplicationContext(), android.net.Uri.parse(uri.toASCIIString()), headers);
		} catch (IllegalArgumentException | IOException | IllegalStateException | SecurityException | URISyntaxException e) {
			e.printStackTrace();
		}
		mediaPlayer.prepareAsync();
		mediaPlayer.setOnPreparedListener(new OnPreparedListener() {

			@Override
			public void onPrepared(MediaPlayer arg0) {
				// Buffered enough to play
				prepared = true;
				play();
			}
		});
		mediaPlayer.setOnCompletionListener(new OnCompletionListener() {

			@Override
			public void onCompletion(MediaPlayer mp) {
				playPause = false;
				if (prepared) {
					mediaPlayer.pause();
					mediaPlayer.seekTo(0);
				}
				sendBroadcast(STOP);
			}
		});
		mediaPlayer.setOnErrorListener(new OnErrorListener() {
			@Override
			public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
				active = false;
				prepared = false;
				return false;
			}
		});
	}

	public int getCurrentPosition() {
		if (mediaPlayer != null && isPlaying()) {
			return (int) (((float) mediaPlayer.getCurrentPosition() / mediaPlayer.getDuration()) * 100);
		}
		return 0;
	}

	public void sendBroadcast(String what) {
		Intent intent = new Intent();
		intent.setAction(what);
		sendBroadcast(intent);
	}

	public void buildNotification() {
		Intent intent = new Intent(this, RemoteFiles.class);
		PendingIntent pIntent = PendingIntent.getActivity(this, 0, intent, 0);

		Intent change = new Intent(CHANGE_PLAY);
		PendingIntent pChange = PendingIntent.getBroadcast(this, 0, change, 0);

		Intent stop = new Intent(STOP);
		PendingIntent pStop = PendingIntent.getBroadcast(this, 0, stop, 0);

		RemoteViews remoteView = new RemoteViews(getPackageName(), R.layout.notification);
		remoteView.setImageViewResource(R.id.notifimage, R.drawable.ic_play_circle);

		if (mediaPlayer.isPlaying()) {
			remoteView.setImageViewResource(R.id.notifbutton, R.drawable.ic_pause_black);
		}
		else {
			remoteView.setImageViewResource(R.id.notifbutton, R.drawable.ic_play_black);
		}
		remoteView.setTextViewText(R.id.notiftitle, currentPlaying.getFilename());
		remoteView.setOnClickPendingIntent(R.id.notifbutton, pChange);
		remoteView.setOnClickPendingIntent(R.id.notifexit, pStop);

		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this);
		mBuilder.setContent(remoteView)
				.setContentIntent(pIntent)
				.setOngoing(false)
				.setSmallIcon(R.drawable.ic_play);

		Notification noti = mBuilder.build();

		startForeground(NOFITICATION_ID, noti);
	}

	public class AudioBCReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(CHANGE_PLAY)) {
				togglePlay();
			}
			else if (intent.getAction().equals(STOP)) {
				stop();
			}
		}
	}
}