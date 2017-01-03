/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.myhexaville.walkie_talkie;


import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import static com.myhexaville.walkie_talkie.MainActivity.sRecordedFileName;

public final class WebSocketClient extends WebSocketListener {
    private static final String LOG_TAG = "WebSocketClient";
    public static final String START = "start";
    public static final String END = "end";

    private final Context mContext;
    static List<byte[]> sList = new ArrayList<>();
    WebSocket mSocket;
    private MediaPlayer mPlayer;


    public WebSocketClient(Context c) {
        mContext = c;
    }

    public void run() {
        OkHttpClient client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build();

        Request request = new Request.Builder()
                .url("ws://mysterious-wildwood-33130.herokuapp.com/chat")
                .build();
        client.newWebSocket(request, this);

        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        client.dispatcher().executorService().shutdown();
    }

    @Override
    public void onOpen(final WebSocket webSocket, Response response) {
        Log.d(LOG_TAG, "onOpen: ");
        mSocket = webSocket;
    }

    public void sendAudio() {
        FileChannel in = null;

        try {
            File f = new File(sRecordedFileName);
            in = new FileInputStream(f).getChannel();

            mSocket.send(START);

            sendAudioBytes(in);

            mSocket.send(END);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendAudioBytes(FileChannel in) throws IOException {
        ByteBuffer buff = ByteBuffer.allocateDirect(32);

        while (in.read(buff) > 0) {
            buff.flip();
            String bytes = ByteString.of(buff).toString();
            mSocket.send(bytes);
            buff.clear();
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (text.equals(START)) {
            sList.clear();
        } else if (text.equals(END)) {
            playReceivedFile();
        } else {
            try {
                String hexValue = text.substring(text.indexOf("hex=") + 4, text.length() - 1);
                ByteString d = ByteString.decodeHex(hexValue);
                byte[] bytes = d.toByteArray();

                sList.add(bytes);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        Log.d(LOG_TAG, "onMessage: " + bytes.toByteArray());
        sList.add(bytes.toByteArray());
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        Log.d(LOG_TAG, "onClosing: " + reason);
        webSocket.close(1000, null);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(LOG_TAG, "onFailure: ", t);
        t.printStackTrace();
    }

    private void playReceivedFile() {
        File f = buildAudioFileFromReceivedBytes();

        playAudio(f);
    }

    @NonNull
    private File buildAudioFileFromReceivedBytes() {
        File f = new File(mContext.getCacheDir().getAbsolutePath() + "/received.3gp");
        try {
            f.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileOutputStream out = null;
        try {
            out = (new FileOutputStream(f));
        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            for (byte[] b : sList) {
                out.write(b);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return f;
    }

    private void playAudio(File f) {
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(mContext, Uri.parse(f.getPath()));
            mPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(LOG_TAG, "onClosing: dudation in millis: " + mPlayer.getDuration());

        mPlayer.start();
    }

}
