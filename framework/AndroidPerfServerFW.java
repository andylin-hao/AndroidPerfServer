package com.androidperf.server;

import android.app.ActivityThread;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AndroidPerfServerFW extends Thread {
    private static final String TAG = "AndroidPerfFW";
    private static final String MSG_END = "PERF_MSG_END\n";

    public static String SOCKET_ADDRESS = "AndroidPerfFW";
    NetworkStatsManager networkStatsManager = null;
    PackageManager packageManager = null;

    private Context systemContext = null;

    public static void main(String[] args) {
        AndroidPerfServerFW server = new AndroidPerfServerFW();
        Looper.prepareMainLooper();
        server.systemContext = ActivityThread.systemMain().getSystemContext();
        server.networkStatsManager = (NetworkStatsManager) server.systemContext.getSystemService("netstats");
        server.packageManager = server.systemContext.getPackageManager();

        server.start();
        try {
            server.join();
        } catch (Exception e) {
            Log.e(TAG, "failed to join");
        }
        System.exit(0);
    }

    private void handleData(OutputStream outputStream, String data) {
        if (data.contains("network ")) {
            try {
                int uid = Integer.parseInt(data.split(" ")[1]);
                dumpNetworkStats(outputStream, uid);
            } catch (Exception e) {
                Log.e(TAG, "network stats command corrupted");
            }
        } else if (data.contains("PING")) {
            writeMSG(outputStream, "OKAY".getBytes());
        } else if (data.contains("convert")) {
            //todo 返回的内容
            String packageName = data.substring(data.indexOf("convert ")+"convert ".length());
            String AppName = getAppName(packageName);
            writeMSG(outputStream, packageName.getBytes());
        }
    }

    private String getAppName(String packageName) {
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName, 0);
        } catch (Exception e) {
            Log.e(TAG, "Cannot resolve the name of " + packageName);
        }
        String name = applicationInfo != null ? packageManager.getApplicationLabel(applicationInfo).toString()
                : "Unknown";
        return name;
    }

    private void dumpNetworkStats(OutputStream outputStream, int uid) {
        try {
            NetStatsData netStats = new NetStatsData();
            NetworkStats.Bucket bucket = new NetworkStats.Bucket();

            Method setPollForce = null;
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    setPollForce = networkStatsManager.getClass().getMethod("setPollForce", boolean.class);
                    setPollForce.invoke(networkStatsManager, true);
                } catch (Exception e) {
                    Log.e(TAG, "cannot find setPollForce");
                }
            }
            NetworkStats querySummaryWiFi = networkStatsManager.querySummary(1, (String) null, Long.MIN_VALUE,
                    Long.MAX_VALUE);
            querySummaryWiFi.close();
            if (setPollForce != null) {
                try {
                    setPollForce.invoke(networkStatsManager, false);
                } catch (Exception e) {
                    Log.e(TAG, "cannot invoke setPollForce");
                }
            }

            NetworkStats querySummaryMobile = networkStatsManager.querySummary(0, (String) null, Long.MIN_VALUE,
                    Long.MAX_VALUE);
            querySummaryMobile.close();

            while (querySummaryWiFi.getNextBucket(bucket)) {
                if (uid == bucket.getUid() && bucket.getTag() == 0) {
                    netStats.mRxBytes += bucket.getRxBytes();
                    netStats.mRxPackets += bucket.getRxPackets();
                    netStats.mTxBytes += bucket.getTxBytes();
                    netStats.mTxPackets += bucket.getTxPackets();
                }
            }
            while (querySummaryMobile.getNextBucket(bucket)) {
                if (uid == bucket.getUid() && bucket.getTag() == 0) {
                    netStats.mRxBytes += bucket.getRxBytes();
                    netStats.mRxPackets += bucket.getRxPackets();
                    netStats.mTxBytes += bucket.getTxBytes();
                    netStats.mTxPackets += bucket.getTxPackets();
                }
            }

            outputStream.write(netStats.toBytes());
            outputStream.write(MSG_END.getBytes());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void writeMSG(OutputStream outputStream, byte[] data) {
        try {
            outputStream.write(data);
            outputStream.write(MSG_END.getBytes());
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];
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
            if (null == server) {
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

            StringBuilder msg = new StringBuilder();
            int msgEnd;
            while (receiver != null) {
                try {
                    len = input.read(buffer);
                    if (len > 0) {
                        msg.append(new String(buffer, 0, len));
                        Log.d(TAG, "receive client msg: " + msg.toString());
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
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(0, mRxBytes);
            buffer.putLong(8, mRxPackets);
            buffer.putLong(16, mTxBytes);
            buffer.putLong(24, mTxPackets);
            return buffer.array();
        }
    }
}