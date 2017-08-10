package com.orin.anders.orinmusicplayer;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;

import java.util.ArrayList;

import android.content.ContentUris;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.PowerManager;
import android.util.Log;

public class MusicService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {

    private AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener;
    private AudioManager audioManager;


    private static final String TAG = "Debug Message";
    private MediaPlayer mediaPlayer;
    private ArrayList<Song> songs;
    private int songPosition;
    private int songCurrentPositionMillisec;
    private int audioFocusResult;
    private final IBinder musicBind = new MusicBinder();
    private IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    private BecomingNoisyReceiver becomingNoisyReceiver;

    public void onCreate() {
        super.onCreate();
        songPosition = 0;

        //songCurrentPositionMillisec set to 0 in onCreate, onCompletion and setSong

        mediaPlayer = new MediaPlayer();
        songCurrentPositionMillisec = 0;
        becomingNoisyReceiver = new BecomingNoisyReceiver();
        initMusicPlayer();
    }

    //initialize listeners, audio focus, mediaplayer
    public void initMusicPlayer() {
        mediaPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int focusChange) {
                switch (focusChange) {
                    case AudioManager.AUDIOFOCUS_GAIN:
                        Log.i(TAG, "AUDIOFOCUS_GAIN");
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        Log.i(TAG, "AUDIOFOCUS_GAIN_TRANSIENT");
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                        Log.i(TAG, "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK");
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
						pauseSong();
                        Log.e(TAG, "AUDIOFOCUS_LOSS");
						break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
						pauseSong();
                        Log.e(TAG, "AUDIOFOCUS_LOSS_TRANSIENT");
                        // Temporary loss of audio focus - expect to get it back - you can keep your resources around
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        pauseSong();
                        Log.e(TAG, "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                        // Lower the volume or pause
                        break;
                }
            }
        };

    }

    public void setList(ArrayList<Song> theSongs) {
        songs = theSongs;
    }

    class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mediaPlayer.pause();
        mediaPlayer.release();
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        //set songCurrentTimeMillisec to 0 so next song will actually start from the beginning
        songCurrentPositionMillisec = 0;
        //nextSong(); //TODO nextsong() throws nullpointer at this point
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        this.mediaPlayer.seekTo(songCurrentPositionMillisec);
        mediaPlayer.start();
    }


    //listens for audio_noisy intent, resets mediaPlayer, get chosen song
    public void playSong() {
        //request audio focus
        audioFocusResult = audioManager.requestAudioFocus(onAudioFocusChangeListener,
                AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        if (audioFocusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {

            //listen for noise, i.e unplugged headphones
            registerReceiver(becomingNoisyReceiver, intentFilter);
            mediaPlayer.reset();
            Song playSong = songs.get(songPosition);
            long currSong = playSong.getId();
            Uri trackUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, currSong);
            try {
                mediaPlayer.setDataSource(getApplicationContext(), trackUri);
            } catch (Exception e) {
                Log.e("MUSIC SERVICE", "Error setting data source.");
            }
            //onPrepared() calls mediaPlayer.Start()
            mediaPlayer.prepareAsync();
            //TODO should also set button to button_play
        }
    }

    public void nextSong() {
        Log.d(TAG, "getCurrentPosition value: " + String.valueOf(mediaPlayer.getCurrentPosition()));
        //Log.d(TAG, "songlist size value: " + String.valueOf(songs.size()));
        if (songPosition < (songs.size() - 1)) {
            Log.d(TAG, "nextSong() value before : " + String.valueOf(songPosition));
            songPosition++;
            Log.d(TAG, "nextSong() value after ++ : " + String.valueOf(songPosition));
            playSong();
        } else {
            Log.d(TAG, "nextSong() !songPosition < songs.size, songPosition: " + String.valueOf(songPosition));
            songPosition = 0;
            playSong();
        }
    }
	
	public void prevSong() {
        Log.d(TAG, "getCurrentPosition value: " + String.valueOf(mediaPlayer.getCurrentPosition()));
        if (mediaPlayer.getCurrentPosition() > 1500) {
            Log.d(TAG, "getCurrentPosition value > 1000: " + String.valueOf(mediaPlayer.getCurrentPosition()));
            songCurrentPositionMillisec = 0;
            Log.d(TAG, "song restarted, songPosition: " + String.valueOf(songPosition));
            playSong();
        } else if (mediaPlayer.getCurrentPosition() <= 1500 && songPosition > 0) {
            Log.d(TAG, "songPosition value before: " + String.valueOf(songPosition));
            songPosition--;
            songCurrentPositionMillisec = 0;
            Log.d(TAG, "songPosition value after: " + String.valueOf(songPosition));
            playSong();
        } else if (mediaPlayer.getCurrentPosition() <= 1500 && songPosition == 0){
            songPosition = songs.size() - 1;
            Log.d(TAG, "songPosition == 0, play last song on list, songPosition: " + String.valueOf(songPosition));
            songCurrentPositionMillisec = 0;
			playSong();
		}
    }

    public void pauseSong() {
        unregisterReceiver(becomingNoisyReceiver);
        mediaPlayer.pause();
        Log.d(TAG, "songPosition value: " + String.valueOf(songPosition));
        Log.d(TAG, "getCurrentPosition value: " + String.valueOf(mediaPlayer.getCurrentPosition()));
        //TODO should also set button to button_pause
    }
	
	public void stopSong(){
		unregisterReceiver(becomingNoisyReceiver);
		mediaPlayer.pause();
	}

    public void setSong(int songIndex) {
        songPosition = songIndex;
        songCurrentPositionMillisec = 0;
    }

    public boolean getIsPlaying() {
        return mediaPlayer.isPlaying();
    }


}
