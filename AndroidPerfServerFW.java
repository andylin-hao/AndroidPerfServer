package com.androidperf.server;

import android.app.ActivityThread;
import android.content.Context;
import android.os.Looper;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class AndroidPerfServerFW extends Thread {
    private static final String TAG = "AndroidPerfFW";
    private static final String MSG_END = "PERF_MSG_END\n";

    public static String SOCKET_ADDRESS = "AndroidPerfServer";
    NetworkStatsManager networkStatsManager = null;

    private Context systemContext = null;

    public static void main(String[] args) {
        AndroidPerfServerFW server = new AndroidPerfServerFW();
        Looper.prepareMainLooper();
        server.systemContext = ActivityThread.systemMain().getSystemContext();
        server.networkStatsManager = (NetworkStatsManager) server.systemContext.getSystemService("netstats");

        server.start();
        Looper.loop();
    }

    private void handleData(OutputStream outputStream, String data) {
        if (data.contains("network ")) {
            int uid = Integer.parseInt(data.split(" ")[1]);
            dumpNetworkStats(outputStream, uid);
        }
    }

    private void dumpNetworkStats(OutputStream outputStream, int uid) {

    }

    @Override
    public void run() {
        try {
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            networkStatsManager.setPollForce(true);
            NetworkStats querySummary = networkStatsManager.querySummary(1, (String) null, Long.MIN_VALUE, Long.MAX_VALUE);
            querySummary.close();
            networkStatsManager.setPollForce(false);
            while (querySummary.getNextBucket(bucket)) {
                System.out.println(bucket.getUid() + " Bytes: " + bucket.getRxBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        byte[] buffer = new byte[1024];
        StringBuilder reply = new StringBuilder();
        int msgEnd;
        InputStream input;
        int len;
        LocalServerSocket server;
        LocalSocket receiver;

        try {
            server = new LocalServerSocket(SOCKET_ADDRESS);
        } catch (IOException e) {
            Log.e(TAG, "failed to create server");
            e.printStackTrace();
            return;
        }

        LocalSocketAddress localSocketAddress; 
        localSocketAddress = server.getLocalSocketAddress();
        String str = localSocketAddress.getName();

        while (true) {
            if (null == server){
                Log.e(TAG, "server is null");
                break;
            }

            try {
                receiver = server.accept();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                continue;
            }                   

            try {
                input = receiver.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                continue;
            }

            Log.d(TAG, "client connected");

            while (receiver != null) {
                try {
                    len = input.read(buffer);
                    if (len > 0) {
                        reply.append(new String(buffer, 0, len));
                    }
                    if (len <= 0) {
                        String replyStr = reply.toString();
                    }
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                    break;
                }
            }
        }
    }
}