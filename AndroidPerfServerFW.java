package com.androidperf.server;

import android.app.ActivityThread;
import android.content.Context;
import android.os.Looper;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;

import java.io.IOException;
import java.io.InputStream;

public class AndroidPerfServerFW extends Thread {
    public static String SOCKET_ADDRESS = "AndroidPerfServer";
    NetworkStatsManager networkStatsManager = null;

    private Context systemContext = null;
    int bufferSize = 32;
    byte[] buffer;
    int bytesRead;
    int totalBytesRead;
    int posOffset;
    LocalServerSocket server;
    LocalSocket receiver;
    InputStream input;

    public static void main(String[] args) {
        AndroidPerfServerFW server = new AndroidPerfServerFW();
        Looper.prepareMainLooper();
        server.systemContext = ActivityThread.systemMain().getSystemContext();
        server.networkStatsManager = (NetworkStatsManager) server.systemContext.getSystemService("netstats");
        System.out.println(server.networkStatsManager);
        try {
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();
            server.networkStatsManager.setPollForce(true);
            NetworkStats querySummary = server.networkStatsManager.querySummary(1, (String) null, Long.MIN_VALUE, Long.MAX_VALUE);
            querySummary.close();
            server.networkStatsManager.setPollForce(false);
            while (querySummary.getNextBucket(bucket)) {
                System.out.println(bucket.getUid() + " Bytes: " + bucket.getRxBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        server.start();
    }

    @Override
    public void run() {

        buffer = new byte[bufferSize];
        bytesRead = 0;
        totalBytesRead = 0;
        posOffset = 0;

        try {
            server = new LocalServerSocket(SOCKET_ADDRESS);
        } catch (IOException e) {
            System.out.println("The localSocketServer created failed !!!");
            e.printStackTrace();
        }

        LocalSocketAddress localSocketAddress; 
        localSocketAddress = server.getLocalSocketAddress();
        String str = localSocketAddress.getName();

        while (true) {

            if (null == server){
                System.out.println("The localSocketServer is NULL !!!");
                break;
            }

            try {
                System.out.println("localSocketServer begins to accept()");
                receiver = server.accept();
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }                   

            try {
                input = receiver.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }

            System.out.println("The client connect to LocalServerSocket");

            while (receiver != null) {

                try {
                    bytesRead = input.read(buffer, posOffset,
                            (bufferSize - totalBytesRead));
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }

                if (bytesRead >= 0) {
                    System.out.println("Receive data from socket, bytesRead = "
                            + bytesRead);
                    posOffset += bytesRead;
                    totalBytesRead += bytesRead;
                }

                String msg = new String(buffer);
                System.out.println("The context of buffer is : " + msg);

                bytesRead = 0;
                totalBytesRead = 0;
                posOffset = 0;
            }
        }
    }
}