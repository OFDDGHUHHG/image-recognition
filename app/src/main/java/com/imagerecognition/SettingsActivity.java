package com.imagerecognition;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText etToken;
    private EditText etApiKey;
    private EditText etSecretKey;
    private TextView tvHelp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etToken = findViewById(R.id.etToken);
        etApiKey = findViewById(R.id.etApiKey);
        etSecretKey = findViewById(R.id.etSecretKey);
        tvHelp = findViewById(R.id.tvHelp);
        Button btnSave = findViewById(R.id.btnSave);

        // Load saved settings
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        etToken.setText(prefs.getString("baidu_token", ""));
        etApiKey.setText(prefs.getString("api_key", ""));
        etSecretKey.setText(prefs.getString("secret_key", ""));

        tvHelp.setText("如何获取百度识物Token：\n\n" +
                "1. 访问百度AI开放平台：ai.baidu.com\n" +
                "2. 注册/登录账号\n" +
                "3. 创建应用，获取API Key和Secret Key\n" +
                "4. 在应用中开通以下免费服务：\n" +
                "   - 通用物体和场景识别\n" +
                "   - 通用文字识别\n" +
                "5. 使用下方按钮获取Access Token\n\n" +
                "注意：\n" +
                "- Access Token有效期30天，过期需重新获取\n" +
                "- 物体识别和文字识别共用同一个Token\n" +
                "- 物体识别免费500次/天\n" +
                "- 文字识别免费500次/天");

        btnSave.setOnClickListener(v -> saveSettings());
        findViewById(R.id.btnGetToken).setOnClickListener(v -> getToken());
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = getSharedPreferences("settings", MODE_PRIVATE).edit();
        editor.putString("baidu_token", etToken.getText().toString().trim());
        editor.putString("api_key", etApiKey.getText().toString().trim());
        editor.putString("secret_key", etSecretKey.getText().toString().trim());
        editor.apply();
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }

    private void getToken() {
        String apiKey = etApiKey.getText().toString().trim();
        String secretKey = etSecretKey.getText().toString().trim();

        if (apiKey.isEmpty() || secretKey.isEmpty()) {
            Toast.makeText(this, "请先填写API Key和Secret Key", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                String token = fetchAccessToken(apiKey, secretKey);
                runOnUiThread(() -> {
                    if (token != null && !token.contains("error")) {
                        etToken.setText(token);
                        saveSettings();
                        Toast.makeText(this, "Token获取成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "获取Token失败：" + token, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "获取Token失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String fetchAccessToken(String apiKey, String secretKey) throws Exception {
        String url = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;

        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        conn.disconnect();

        String json = response.toString();
        // Parse access_token from JSON
        int start = json.indexOf("\"access_token\":\"") + 16;
        int end = json.indexOf("\"", start);
        if (start > 15 && end > start) {
            return json.substring(start, end);
        }
        return json;
    }
}
