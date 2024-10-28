package com.example.ovenrumputlaut;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Timer;
import java.util.TimerTask;

public class Home extends AppCompatActivity {

    private ToggleButton tglRelay;
    private TextView tVSuhu;
    private TextView tVKelembapan;
    private TextView tVStatus;
    private TextView timerText;
    private Button btnReset;
    private String relayStatus;
    private String id;

    private Handler handler = new Handler();
    private Runnable runnable;
    private final int DELAY = 5000; // 5 detik

    private Timer timer;
    private TimerTask timerTask;
    private double time = 0.0;
    private boolean timerStarted = false;

    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        time = Double.longBitsToDouble(sharedPreferences.getLong("timer_time", 0L));
        timerStarted = sharedPreferences.getBoolean("timer_started", false);

        init();
        id ="1";
        cekStatusRelay();
        tVStatus.setText("");

        timer = new Timer();

        if (timerStarted) {
            startTimer();
        }

        tglRelay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (tglRelay.isChecked()){
                    relayStatus = "1";
                    if (!timerStarted) {
                        startTimer();
                    }
                    setRelay();
                    bacaSensorDHT();
                    btnReset.setVisibility(View.INVISIBLE);
                }else{
                    relayStatus = "0";
                    stopTimer();
                    setRelay();
                    btnReset.setVisibility(View.VISIBLE);
                }
            }
        });

        btnReset.setOnClickListener(view -> {
            resetTimer();
        });
    }

    private void startTimer() {
        timerStarted = true;
        timerTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        time++;
                        timerText.setText(getTimerText());  // Update TextView dengan nilai timer
                    }
                });
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 1000);
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerStarted = false;
        }
    }

    private void resetTimer() {
        if (timerTask != null) {
            timerTask.cancel();
        }
        time = 0.0;
        timerStarted = false;
        timerText.setText(getTimerText());  // Reset TextView ke 00:00:00
    }

    private String getTimerText() {
        int rounded = (int) Math.round(time);

        int seconds = ((rounded % 86400) % 3600) % 60;
        int minutes = ((rounded % 86400) % 3600) / 60;
        int hours = ((rounded % 86400) / 3600);

        return formatTime(seconds, minutes, hours);
    }

    private String formatTime(int seconds, int minutes, int hours) {
        return String.format("%02d", hours) + " : " + String.format("%02d", minutes) + " : " + String.format("%02d", seconds);
    }

    private void bacaSensorDHT(){
        String url = getString(R.string.api_server) + "/baca-sensorDHT";

        runnable = new Runnable() {
            @Override
            public void run() {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Http http = new Http(Home.this, url);
                        http.send();

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Integer code = http.getStatusCode();
                                if (code == 200) {
                                    try {
                                        JSONObject response = new JSONObject(http.getResponse());
                                        String suhu = response.getString("suhu");
                                        String kelembapan = response.getString("kelembapan");

                                        tVSuhu.setText("Suhu Oven : " + suhu + "C");
                                        tVKelembapan.setText("Kelembapan Oven : " + kelembapan+" %");

                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    Toast.makeText(Home.this, "Error " + code, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    }
                }).start();

                // Mengulangi runnable setiap beberapa detik
                handler.postDelayed(runnable, DELAY);
            }
        };

        // Memulai polling
        handler.post(runnable);
    }

    @Override
    protected void onStop() {
        super.onStop();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("timer_time", Double.doubleToRawLongBits(time));
        editor.putBoolean("timer_started", timerStarted);
        editor.apply();
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    private void cekStatusRelay(){
        String url = getString(R.string.api_server)+"/baca-relay";

        new Thread(new Runnable() {
            @Override
            public void run() {
                Http http = new Http(Home.this, url);
                http.send();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Integer code = http.getStatusCode();
                        if (code == 200){
                            try {
                                // Mengambil response sebagai string dan mengonversinya menjadi integer
                                String responseString = http.getResponse();
                                int relayStatus = Integer.parseInt(responseString.trim());

                                // Gunakan relayStatus sesuai kebutuhan
                                if (relayStatus == 0){
                                    tglRelay.setChecked(false);
                                }else{
                                    tglRelay.setChecked(true);
                                }

                            } catch (NumberFormatException e) {
                                e.printStackTrace();
                                Toast.makeText(Home.this, "Error parsing response", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(Home.this, "Error "+code, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }


    private void setRelay(){
        JSONObject params = new JSONObject();
        try {
            params.put("relay", relayStatus);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String data = params.toString();
        String url = getString(R.string.api_server)+"/update/"+id;

        new Thread(new Runnable() {
            @Override
            public void run() {
                Http http = new Http(Home.this, url);
                http.setMethod("put");
                http.setData(data);
                http.send();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Integer code = http.getStatusCode();
                        if (code == 200){
                            if (relayStatus=="1"){
                                tVStatus.setText("Oven Menyala");
                            }else{
                                tVStatus.setText("Oven Mati");
                            }
                            try {
                                JSONObject response = new JSONObject(http.getResponse());

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                        else if (code == 422) {
                            try {
                                JSONObject response = new JSONObject(http.getResponse());
                                String msg = response.getString("message");
                                alertFail(""+msg);
                                //Toast.makeText(Register.this, ""+msg, Toast.LENGTH_LONG).show();

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                        }
                        else if (code == 401) {
                            try {
                                JSONObject response = new JSONObject(http.getResponse());
                                String msg = response.getString("message");
                                alertFail(""+msg);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }

                        }
                        else{
                            Toast.makeText(Home.this, "Error "+code, Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();


    }

    private void alertFail(String s) {
        new AlertDialog.Builder(this)
                .setTitle("Failed")
                .setIcon(R.drawable.baseline_error_24)
                .setMessage(s)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                }).show();
    }

    private void init(){
        tglRelay = findViewById(R.id.tglRelay);
        tVSuhu = findViewById(R.id.tVSuhu);
        tVKelembapan = findViewById(R.id.tVKelembapan);
        tVStatus = findViewById(R.id.tVStatus);
        timerText = findViewById(R.id.timerText);
        btnReset = findViewById(R.id.btnReset);

        btnReset.setVisibility(View.INVISIBLE);
    }
}