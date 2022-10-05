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
import java.nio.ByteBuffer;

public class AndroidPerfServerFW extends Thread {
    private static final String TAG = "AndroidPerfFW";
    private static final String MSG_END = "PERF_MSG_END\n";

    public static String SOCKET_ADDRESS = "AndroidPerfFW";
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
            Log.d(TAG, "Network stats");
            int uid = Integer.parseInt(data.split(" ")[1]);
            dumpNetworkStats(outputStream, uid);
        } else if (data.contains("PING")) {
            writeMSG(outputStream, "OKAY".getBytes());
        }
    }

    private void dumpNetworkStats(OutputStream outputStream, int uid) {
        try {
            NetStatsData mobileStats = new NetStatsData();
            NetStatsData wifiStats = new NetStatsData();
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();

            networkStatsManager.setPollForce(true);
            NetworkStats querySummaryWiFi = networkStatsManager.querySummary(1, (String) null, Long.MIN_VALUE, Long.MAX_VALUE);
            querySummaryWiFi.close();
            networkStatsManager.setPollForce(false);

            NetworkStats querySummaryMobile = networkStatsManager.querySummary(0, (String) null, Long.MIN_VALUE, Long.MAX_VALUE);
            querySummaryMobile.close();

            while (querySummaryWiFi.getNextBucket(bucket)) {
                if (uid == bucket.getUid() && bucket.getTag() == 0) {
                    wifiStats.mRxBytes += bucket.getRxBytes();
                    wifiStats.mRxPackets += bucket.getRxPackets();
                    wifiStats.mTxBytes += bucket.getTxBytes();
                    wifiStats.mTxPackets += bucket.getTxPackets();
                }
            }
            while (querySummaryMobile.getNextBucket(bucket)) {
                if (uid == bucket.getUid() && bucket.getTag() == 0) {
                    mobileStats.mRxBytes += bucket.getRxBytes();
                    mobileStats.mRxPackets += bucket.getRxPackets();
                    mobileStats.mTxBytes += bucket.getTxBytes();
                    mobileStats.mTxPackets += bucket.getTxPackets();
                }
            }

            outputStream.write(wifiStats.toBytes());
            outputStream.write(mobileStats.toBytes());
            outputStream.write(MSG_END.getBytes());
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }
    }

    private void writeMSG(OutputStream outputStream, byte[] data) {
        try {
            outputStream.write(data);
            outputStream.write(MSG_END.getBytes());
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
        StringBuilder msg = new StringBuilder();
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
            
            while (receiver != null && receiver.isConnected()) {
                try {
                    len = input.read(buffer);
                    if (len > 0) {
                        msg.append(new String(buffer, 0, len));
                    }
                    msgEnd = msg.indexOf(MSG_END);
                    if (msgEnd != -1) {
                        String msgStr = msg.toString().substring(0, msgEnd);
                        Log.d(TAG, "receive client msg: " + msgStr);
                        handleData(receiver.getOutputStream(), msgStr);
                        break;
                    }
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                    break;
                }
            }
        }
    }

    class NetStatsData {
        public long mRxBytes = 0;
        public long mRxPackets = 0;
        public long mTxBytes = 0;
        public long mTxPackets = 0;

        public byte[] toBytes() {
            ByteBuffer buffer = ByteBuffer.allocate(32);
            buffer.putLong(0, mRxBytes);
            buffer.putLong(8, mRxPackets);
            buffer.putLong(16, mTxBytes);
            buffer.putLong(24, mTxPackets);
            return buffer.array();
        }
    }
}