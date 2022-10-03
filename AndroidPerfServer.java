package com.androidperf.server;

import android.app.ActivityThread;
import android.content.Context;
import android.os.Looper;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.pm.IPackageManager;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SELinux;
import android.os.ServiceManager;
import android.os.ShellCallback;
import android.os.UserHandle;
import android.util.AndroidException;

import com.android.internal.os.BaseCommand;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;

public class AndroidPerfServer {
    private Context systemContext = null;

    public static void main(String[] args) {
        Server server = new Server();
        Looper.prepareMainLooper();
        server.systemContext = ActivityThread.systemMain().getSystemContext();
    }
}