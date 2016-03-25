package com.leonziyo.test;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.leonziyo.javainspector.Updater;


public class MainActivity extends AppCompatActivity {

    private int testInt;
    public char testChar;
    private boolean testBool;
    private float testFloat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Updater updater = Updater.getInstance(
                this.getClass().getPackage().getName(),
                "http://192.168.1.72:8080/" //change for your own server ip, check documentaiton!
        );

        updater.register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        Updater.getInstance().stopTimer();
    }

    @Override
    public void onResume() {
        super.onResume();
        Updater.getInstance().startTimer();
    }
}
