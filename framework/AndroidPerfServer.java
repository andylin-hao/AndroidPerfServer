package com.androidperf.server;

import android.app.ActivityThread;
import android.content.Context;
import android.os.Looper;
import android.os.Build;
import android.os.Process;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.util.Log;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AndroidPerfServer {
    private static final String TAG = "AndroidPerfServer";
    private static final String MSG_END = "PERF_MSG_END\n";

    NetworkStatsManager networkStatsManager = null;
    PackageManager packageManager = null;

    private Context systemContext = null;

    public native int nativeMain();
    public native void nativeWrite(byte[] data, int fd);

    public static void main(String[] args) {
        System.load("/data/local/tmp/libandroidperf.so");
        AndroidPerfServer server = new AndroidPerfServer();
        Looper.prepareMainLooper();
        server.systemContext = ActivityThread.systemMain().getSystemContext();
        server.networkStatsManager = (NetworkStatsManager) server.systemContext.getSystemService("netstats");
        server.packageManager = server.systemContext.getPackageManager();
        Process.setArgV0("AndroidPerfServer");
        System.exit(server.nativeMain());
    }

    private void handleData(int fd, String data) {
        if (data.contains("network ")) {
            try {
                int uid = Integer.parseInt(data.split(" ")[1]);
                dumpNetworkStats(fd, uid);
            } catch (Exception e) {
                Log.e(TAG, "network stats command corrupted");
            }
        } else if (data.contains("PING")) {
            writeMSG(fd, "OKAY".getBytes());
        }
    }

    private String getAppName(String packageName) {
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName, 0);
        } catch (Exception e) {
            Log.e(TAG, "Cannot resolve the name of " + packageName);
        }
        String name = applicationInfo != null ? packageManager.getApplicationLabel(applicationInfo).toString() : "Unknown";
        return name;
    }

    private void dumpNetworkStats(int fd, int uid) {
        try {
            NetStatsData mobileStats = new NetStatsData();
            NetStatsData wifiStats = new NetStatsData();
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
            NetworkStats querySummaryWiFi = networkStatsManager.querySummary(1, (String) null, Long.MIN_VALUE, Long.MAX_VALUE);
            querySummaryWiFi.close();
            if (setPollForce != null) {
                try {
                    setPollForce.invoke(networkStatsManager, false);
                } catch (Exception e) {
                    Log.e(TAG, "cannot invoke setPollForce");
                }
            }

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

            nativeWrite(wifiStats.toBytes(), fd);
            nativeWrite(mobileStats.toBytes(), fd);
            nativeWrite(MSG_END.getBytes(), fd);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void writeMSG(int fd, byte[] data) {
        try {
            nativeWrite(data, fd);
            nativeWrite(MSG_END.getBytes(), fd);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    public void onRequest(String data, int fd) {
        Log.d(TAG, "receive native msg: " + data + " " + String.valueOf(fd));
        if (fd > 0) {
            handleData(fd, data);
        } else {
            Log.e(TAG, "invalid fd");
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