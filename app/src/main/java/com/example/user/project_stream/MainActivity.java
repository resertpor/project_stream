package com.example.user.project_stream;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class MainActivity extends AppCompatActivity implements SocketCallback {
    public final int SAMPLE_RATE = 44100;
    public final int ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};
    private boolean permissionToRecordAccepted = false;

    private Button btRecord;
    private RelativeLayout mainLayout;

    //audio
    private AudioTrack audioTrack;
    private AudioRecord audioRecorder;
    private int minBufferSize;

    //stream
    private InputStream socketIs;
    private OutputStream socketOs;

    //control
    private boolean doPlay = true;
    private boolean doRecord = true;
    private PlayThread playThread;
    private RecordThread recordThread;
    SocketTransmitter socketTransmitter;
    Button bt_call, bt_receive;
    EditText et_call;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bt_call = (Button) findViewById(R.id.call);
        et_call = (EditText) findViewById(R.id.et_call);
        bt_receive = (Button) findViewById(R.id.receive);
        socketTransmitter = new SocketTransmitter("192.168.1.40", 1234);
        socketTransmitter.start();

//        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
        bt_call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                socketTransmitter.send(1234, "request_call:99:" + et_call.getText(), MainActivity.this);
            }
        });
        bt_receive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                socketTransmitter.send(1234, "reject:88:0", MainActivity.this);
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }

        if (!permissionToRecordAccepted) {
            Toast.makeText(this, "Need audio permission.", Toast.LENGTH_SHORT).show();
            finish();
        }

        init();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connectServer();
                    playThread.start();
                    recordThread.start();
                    audioRecorder.startRecording();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void connectServer() throws IOException {
        Socket socket = new Socket("192.168.1.40", 1234);
        socketIs = socket.getInputStream();
        socketOs = socket.getOutputStream();
    }

    private void init() {
        minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, ENCODING);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, ENCODING, minBufferSize, AudioTrack.MODE_STREAM);
        audioTrack.play();

        playThread = new PlayThread();
        audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, ENCODING, minBufferSize);
        recordThread = new RecordThread();
    }


    @Override
    protected void onStop() {
        if (audioTrack != null) {
            doPlay = false;
            doRecord = false;
            audioTrack.stop();
            audioTrack.release();
            audioRecorder.stop();
            audioRecorder.release();
        }
        super.onStop();
    }

    @Override
    public void onSocketResult(int requestId, String result) {
        if ((requestId == 1234) && (result.startsWith("waiting"))) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Calling", Toast.LENGTH_SHORT).show();
                }
            });

            socketTransmitter.read(1, this);
        } else if (requestId == 1) {
            if (result.startsWith("reject")) {
                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Reject calling", Toast.LENGTH_SHORT).show();
                    }
                });

            }
        }
        Log.i("123", requestId + "," + result);
    }

    class RecordThread extends Thread {

        @Override
        public void run() {
            BufferedOutputStream bos = new BufferedOutputStream(socketOs);
            int n;
            byte[] data = new byte[minBufferSize];
            while (doRecord) {
                try {
                    n = audioRecorder.read(data, 0, data.length);
                    bos.write(data, 0, n);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            try {
                bos.close();
            } catch (IOException e) {
            }
        }

    }

    class PlayThread extends Thread {

        @Override
        public void run() {
            BufferedInputStream bis = new BufferedInputStream(socketIs);
            int n;
            byte data[] = new byte[minBufferSize];
            while (doPlay) {
                try {
                    n = bis.read(data);
                    audioTrack.write(data, 0, n);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            try {
                bis.close();
            } catch (IOException e) {
            }
        }

    }
}
