package com.googlecode.networklog.Activities;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;

import com.googlecode.networklog.NetworkLogService;

import java.io.File;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = new Intent(this, NetworkLogService.class);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putString("logfile", Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "networklog.txt").apply();
        i.putExtra("logfile", prefs.getString("logfile", null));
        startService(i);
    }
}
