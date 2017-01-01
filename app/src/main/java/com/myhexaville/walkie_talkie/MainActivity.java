package com.myhexaville.walkie_talkie;

import android.Manifest;
import android.animation.ValueAnimator;
import android.databinding.DataBindingUtil;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;

import com.myhexaville.walkie_talkie.databinding.ActivityMainBinding;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;


public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "AudioRecordTest";
    public static final int RC_RECORD_AUDIO = 1000;
    public static String sRecordedFileName;

    private MediaRecorder mRecorder;
    private MediaPlayer mPlayer;
    boolean mStartRecording = true;
    boolean mStartPlaying = true;
    private ActivityMainBinding mBinding;
    private ValueAnimator mAnimator;
    private WebSocketClient client;
    private Socket mSsocket;


    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        sRecordedFileName = getCacheDir().getAbsolutePath() + "/audiorecordtest.3gp";

//        client = new WebSocketClient(this);
//        client.run();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket socket = null;
                    Log.d(LOG_TAG, "run: ");
                    socket = new Socket("192.168.1.214", 1010);
                    Log.d(LOG_TAG, "run: 0");

                    if (mSsocket.isConnected()) {
                        mSsocket = socket;
                        Log.d(LOG_TAG, "run: connected ");
                        mPlayer = new MediaPlayer();
                        try {
                            mPlayer.setDataSource(ParcelFileDescriptor.fromSocket(socket).getFileDescriptor());

                            mPlayer.start();
                        } catch (IOException e) {
                            Log.e(LOG_TAG, "prepare() failed");
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }


    @Override
    public void onPause() {
        super.onPause();
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }


    @AfterPermissionGranted(RC_RECORD_AUDIO)
    private void startRecording() {
        String[] perms = {Manifest.permission.RECORD_AUDIO};
        if (EasyPermissions.hasPermissions(this, perms)) {


            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setOutputFile(ParcelFileDescriptor.fromSocket(mSsocket).getFileDescriptor());
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            try {
                mRecorder.prepare();
            } catch (IOException e) {
                Log.e(LOG_TAG, "prepare() failed");
            }

            mRecorder.start();
        } else {
            EasyPermissions.requestPermissions(this, "Hi", RC_RECORD_AUDIO, perms);
        }
    }

    private void stopRecording() {
//        mRecorder.stop();
//        mRecorder.release();
//        mRecorder = null;
    }

    public void record(View v) {
        if (mStartRecording) {
            startRecording();
            setRecordIcon(false);
        } else {
            stopRecording();
            setRecordIcon(true);
        }

        mStartRecording = !mStartRecording;
    }

    private void setRecordIcon(boolean record) {
        if (record) {
            mBinding.recordBtn
                    .setImageDrawable(VectorDrawableCompat.create(getResources(), R.drawable.ic_fiber_manual_record_black_24dp, getTheme()));
        } else {
            mBinding.recordBtn
                    .setImageDrawable(VectorDrawableCompat.create(getResources(), R.drawable.ic_stop_black_24dp, getTheme()));
        }
    }

    private void setPlayIcon(boolean play) {
        if (play) {
            mBinding.playBtn
                    .setImageDrawable(VectorDrawableCompat.create(getResources(), R.drawable.ic_play_arrow_black_24dp, getTheme()));
        } else {
            mBinding.playBtn
                    .setImageDrawable(VectorDrawableCompat.create(getResources(), R.drawable.ic_stop_black_24dp, getTheme()));
        }
    }


    public void play(View v) {
        if (mStartPlaying) {
            startPlaying();
            setPlayIcon(false);
        } else {
            if (mAnimator != null && mAnimator.isRunning()) {
                mAnimator.cancel();
            }
            mBinding.progress.setProgress(0);

            stopPlaying();
            setPlayIcon(true);
        }
        mStartPlaying = !mStartPlaying;
    }

    private void startPlaying() {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(sRecordedFileName);
            mPlayer.prepare();

            setupProgressAnimation();


            mPlayer.start();
            mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    setPlayIcon(true);
                    mStartPlaying = true;
                    stopPlaying();
                }
            });
        } catch (IOException e) {
            Log.e(LOG_TAG, "prepare() failed");
        }
    }

    private void setupProgressAnimation() {
        setupDurationDisplayTime();

        mBinding.progress.setMax(mPlayer.getDuration());
        mAnimator = ValueAnimator.ofInt(0, mPlayer.getDuration());
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                Integer value = (Integer) animation.getAnimatedValue();
                mBinding.progress.setProgress(value);
                updatePlayedTime(value);
            }
        });
        mAnimator.setDuration(mPlayer.getDuration());
        mAnimator.start();
    }

    private void setupDurationDisplayTime() {
        long millis = mPlayer.getDuration();
        String t = convertMillisToTime(millis);
        mBinding.duration.setText(t);
    }

    private String convertMillisToTime(long millis) {
        return String.format("%d:%02d",
                MILLISECONDS.toMinutes(millis),
                MILLISECONDS.toSeconds(millis) - MINUTES.toSeconds(MILLISECONDS.toMinutes(millis))
        );
    }

    private void updatePlayedTime(long millis) {
        String t = convertMillisToTime(millis);
        mBinding.played.setText(t);
    }

    private void stopPlaying() {
        mPlayer.stop();
        mPlayer.release();
        mPlayer = null;
    }

    public void send(View view) {
        client.sendAudio();
    }

}

