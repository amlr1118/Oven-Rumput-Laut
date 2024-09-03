package com.example.ovenrumputlaut;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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

public class Home extends AppCompatActivity {

    private ToggleButton tglRelay;
    private TextView tVSuhu;
    private TextView tVKelembapan;
    private TextView tVStatus;
    private String relayStatus;
    private String id;

    private Handler handler = new Handler();
    private Runnable runnable;
    private final int DELAY = 5000; // 5 detik
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

        init();
        id ="1";
        cekStatusRelay();
        tVStatus.setText("");

        tglRelay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (tglRelay.isChecked()){
                    relayStatus = "1";
                    setRelay();
                    bacaSensorDHT();
                }else{
                    relayStatus = "0";
                    setRelay();

                }
            }
        });
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
                                tVStatus.setText("Oven Menyala 1");
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
    }
}