package com.googlecode.networklog.Activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.googlecode.networklog.Services.NetworkLogService;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = new Intent(this, NetworkLogService.class);
        startService(i);
    }
}
