package com.imagerecognition;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class RecognitionActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView tvResult;
    private EditText etName;
    private EditText etDescription;
    private Button btnRecognize;
    private Button btnSave;
    private ProgressBar progressBar;
    private Bitmap bitmap;
    private String imagePath;
    private RecognitionDatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recognition);

        dbHelper = new RecognitionDatabaseHelper(this);

        imageView = findViewById(R.id.imageView);
        tvResult = findViewById(R.id.tvResult);
        etName = findViewById(R.id.etName);
        etDescription = findViewById(R.id.etDescription);
        btnRecognize = findViewById(R.id.btnRecognize);
        btnSave = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);

        imagePath = getIntent().getStringExtra("image_path");
        if (imagePath != null) {
            bitmap = BitmapFactory.decodeFile(imagePath);
            imageView.setImageBitmap(bitmap);
        }

        btnRecognize.setOnClickListener(v -> startRecognition());
        btnSave.setOnClickListener(v -> saveRecord());
    }

    private void startRecognition() {
        // Check if Baidu token is configured
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String accessToken = prefs.getString("baidu_token", "");
        
        if (accessToken.isEmpty()) {
            Toast.makeText(this, "请先在设置中配置百度识物Token", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnRecognize.setEnabled(false);
        tvResult.setText("正在识别...");

        new Thread(() -> {
            try {
                String result = recognizeWithBaidu(accessToken);
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnRecognize.setEnabled(true);
                    tvResult.setText(result);
                    parseResult(result);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnRecognize.setEnabled(true);
                    tvResult.setText("识别失败：" + e.getMessage());
                });
            }
        }).start();
    }

    private String recognizeWithBaidu(String accessToken) throws Exception {
        String url = "https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general?access_token=" + accessToken;
        
        // Read image file and convert to base64
        File file = new File(imagePath);
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[(int) file.length()];
        fis.read(buffer);
        fis.close();
        String imageBase64 = android.util.Base64.encodeToString(buffer, android.util.Base64.NO_WRAP);

        // Make HTTP request
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        
        String params = "image=" + imageBase64;
        DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
        dos.writeBytes(params);
        dos.flush();
        dos.close();

        // Read response
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        conn.disconnect();

        return response.toString();
    }

    private void parseResult(String json) {
        try {
            // Simple JSON parsing
            if (json.contains("\"result\"")) {
                int start = json.indexOf("\"keyword\":\"") + 11;
                int end = json.indexOf("\"", start);
                if (start > 10 && end > start) {
                    String keyword = json.substring(start, end);
                    etName.setText(keyword);
                    
                    // Try to get score
                    int scoreStart = json.indexOf("\"score\":\"") + 9;
                    int scoreEnd = json.indexOf("\"", scoreStart);
                    if (scoreStart > 8 && scoreEnd > scoreStart) {
                        String score = json.substring(scoreStart, scoreEnd);
                        etDescription.setText("识别结果：" + keyword + "\n置信度：" + (Float.parseFloat(score) * 100) + "%");
                    }
                }
            } else if (json.contains("error_code")) {
                etDescription.setText("API错误：" + json);
            }
        } catch (Exception e) {
            etDescription.setText("解析结果失败");
        }
    }

    private void saveRecord() {
        String name = etName.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        
        if (name.isEmpty()) {
            Toast.makeText(this, "请输入名称", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save image to internal storage
        String savedImagePath = null;
        if (bitmap != null) {
            try {
                File imageDir = new File(getFilesDir(), "images");
                if (!imageDir.exists()) imageDir.mkdirs();
                savedImagePath = new File(imageDir, System.currentTimeMillis() + ".jpg").getAbsolutePath();
                FileOutputStream fos = new FileOutputStream(savedImagePath);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.close();
            } catch (Exception e) {
                // Ignore image save error
            }
        }

        long id = dbHelper.insertRecord(name, description, savedImagePath);
        if (id > 0) {
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }
}
