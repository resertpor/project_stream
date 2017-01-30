package com.example.user.project_stream;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Objects;

/**
 * Created by User on 23/1/2560.
 */

public class SocketTransmitter extends Thread {

    private Socket socket;
    String host;
    int port;
    Hashtable<Integer, String> message;
    Hashtable<Integer, SocketCallback> callback;
    final Object sleep;

    boolean alive = true;

    SocketTransmitter(String host, int port) {
        this.host = host;
        this.port = port;
        message = new Hashtable<>();
        sleep = new Object();
        callback = new Hashtable<>();
    }

    @Override
    public void run() {
        try {
            socket = new Socket(host, port);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            BufferedInputStream bis = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream bos = new BufferedOutputStream(socket.getOutputStream());

            while (alive) {
                while (message.size() == 0) {
                    try {
                        synchronized (sleep) {
                            sleep.wait();
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                for (int key : message.keySet()) {
                    Log.i("abc", key + "," + message.get(key));
                    byte[] data = new byte[512];
                    if (!message.get(key).isEmpty()) {
                        bos.write(message.get(key).getBytes());
                        bos.flush();
                    }
                    int n = bis.read(data);

                    message.remove(key);
                    SocketCallback temp = callback.get(key);
                    callback.remove(key);
                    temp.onSocketResult(key, new String(data, 0, n));
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void send(int requestId, String message, SocketCallback socketCallback) {
        this.message.put(requestId, message);
        this.callback.put(requestId, socketCallback);
        synchronized (sleep) {
            sleep.notify();
        }
    }

    public void read(int requestId, SocketCallback socketCallback) {
        this.message.put(requestId, "");
        this.callback.put(requestId, socketCallback);
        synchronized (sleep) {
            sleep.notify();
        }
    }

    public InputStream getInputstream() {
        try {
            return socket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public OutputStream getOutputstream() {
        try {
            return socket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
