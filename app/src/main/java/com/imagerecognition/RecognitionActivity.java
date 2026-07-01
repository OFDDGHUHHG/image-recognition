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
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

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
    private String recognizedKeyword = "";
    private String recognizedResult = "";

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

        // Auto-start recognition when entering the page
        startRecognition();

        btnRecognize.setOnClickListener(v -> startRecognition());
        btnSave.setOnClickListener(v -> saveRecord());
    }

    private void startRecognition() {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String accessToken = prefs.getString("baidu_token", "");

        if (accessToken.isEmpty()) {
            Toast.makeText(this, "请先在设置中配置百度识物Token", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }

        if (bitmap == null) {
            Toast.makeText(this, "没有可识别的图片", Toast.LENGTH_SHORT).show();
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
                    tvResult.setText(formatResult(result));
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
        String urlStr = "https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general?access_token=" + accessToken;

        // Compress bitmap to JPEG bytes to ensure correct format
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Resize if too large (max 4MB after base64)
        Bitmap sendBitmap = bitmap;
        int maxDim = 1600;
        if (bitmap.getWidth() > maxDim || bitmap.getHeight() > maxDim) {
            float scale = Math.min((float) maxDim / bitmap.getWidth(), (float) maxDim / bitmap.getHeight());
            int newW = (int) (bitmap.getWidth() * scale);
            int newH = (int) (bitmap.getHeight() * scale);
            sendBitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
        }
        sendBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        byte[] imageBytes = baos.toByteArray();

        // Base64 encode then URL encode
        String imageBase64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);
        String encodedImage = URLEncoder.encode(imageBase64, "UTF-8");

        // Make HTTP request
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        String params = "image=" + encodedImage;
        DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
        dos.write(params.getBytes("UTF-8"));
        dos.flush();
        dos.close();

        // Read response
        int responseCode = conn.getResponseCode();
        BufferedReader reader;
        if (responseCode >= 200 && responseCode < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
        }
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        conn.disconnect();

        return response.toString();
    }

    private String formatResult(String json) {
        try {
            if (json.contains("\"result\"")) {
                // Extract keyword
                int kwStart = json.indexOf("\"keyword\":\"") + 11;
                int kwEnd = json.indexOf("\"", kwStart);
                if (kwStart > 10 && kwEnd > kwStart) {
                    String keyword = json.substring(kwStart, kwEnd);

                    // Extract score
                    String scoreStr = "";
                    int scoreIdx = json.indexOf("\"score\":", kwEnd);
                    if (scoreIdx > 0) {
                        int sStart = scoreIdx + 8;
                        // score could be a number like 0.95 or string "0.95"
                        int sEnd = sStart;
                        while (sEnd < json.length()) {
                            char c = json.charAt(sEnd);
                            if (c == ',' || c == '}' || c == '"') break;
                            sEnd++;
                        }
                        scoreStr = json.substring(sStart, sEnd).replace("\"", "").trim();
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("识别结果: ").append(keyword);
                    if (!scoreStr.isEmpty()) {
                        try {
                            float score = Float.parseFloat(scoreStr);
                            sb.append("\n置信度: ").append(String.format("%.1f%%", score * 100));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    return sb.toString();
                }
            } else if (json.contains("error_code")) {
                return "API错误: " + json;
            }
        } catch (Exception e) {
            return "解析失败: " + e.getMessage();
        }
        return "未能识别出结果";
    }

    private void parseResult(String json) {
        try {
            if (json.contains("\"result\"")) {
                // Extract keyword
                int kwStart = json.indexOf("\"keyword\":\"") + 11;
                int kwEnd = json.indexOf("\"", kwStart);
                if (kwStart > 10 && kwEnd > kwStart) {
                    String keyword = json.substring(kwStart, kwEnd);
                    recognizedKeyword = keyword;

                    // Auto-fill name
                    etName.setText(keyword);

                    // Extract score
                    String scoreStr = "";
                    int scoreIdx = json.indexOf("\"score\":", kwEnd);
                    if (scoreIdx > 0) {
                        int sStart = scoreIdx + 8;
                        int sEnd = sStart;
                        while (sEnd < json.length()) {
                            char c = json.charAt(sEnd);
                            if (c == ',' || c == '}' || c == '"') break;
                            sEnd++;
                        }
                        scoreStr = json.substring(sStart, sEnd).replace("\"", "").trim();
                    }

                    // Build description
                    StringBuilder desc = new StringBuilder();
                    desc.append("识别结果: ").append(keyword);
                    if (!scoreStr.isEmpty()) {
                        try {
                            float score = Float.parseFloat(scoreStr);
                            desc.append("\n置信度: ").append(String.format("%.1f%%", score * 100));
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    // Try to extract root category
                    int rootIdx = json.indexOf("\"root\":\"");
                    if (rootIdx > 0) {
                        int rStart = rootIdx + 8;
                        int rEnd = json.indexOf("\"", rStart);
                        if (rEnd > rStart) {
                            String root = json.substring(rStart, rEnd);
                            desc.append("\n分类: ").append(root);
                        }
                    }

                    recognizedResult = desc.toString();
                    etDescription.setText(desc.toString());
                }
            } else if (json.contains("error_code")) {
                etDescription.setText("识别失败，请检查Token是否正确或重新尝试");
            }
        } catch (Exception e) {
            etDescription.setText("解析结果失败");
        }
    }

    private void saveRecord() {
        String name = etName.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

        // Auto-fill name if empty but we have a recognized keyword
        if (name.isEmpty() && !recognizedKeyword.isEmpty()) {
            name = recognizedKeyword;
            etName.setText(name);
        }

        if (name.isEmpty()) {
            Toast.makeText(this, "请先识别或输入名称", Toast.LENGTH_SHORT).show();
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
