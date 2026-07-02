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
import java.util.ArrayList;
import java.util.List;

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

        // Auto-start recognition
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
        tvResult.setText("正在识别图片中的所有内容...");

        new Thread(() -> {
            try {
                // Prepare image bytes once
                byte[] imageBytes = prepareImageBytes();
                String imageBase64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP);

                // Call object recognition API
                String objectResult = callBaiduApi(
                        "https://aip.baidubce.com/rest/2.0/image-classify/v2/advanced_general",
                        imageBase64, accessToken);

                // Call OCR text recognition API
                String ocrResult = callBaiduApi(
                        "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic",
                        imageBase64, accessToken);

                // Parse and combine results
                String combined = combineResults(objectResult, ocrResult);

                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnRecognize.setEnabled(true);
                    tvResult.setText(combined);
                    autoFillFields(objectResult, ocrResult);
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

    private byte[] prepareImageBytes() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Bitmap sendBitmap = bitmap;
        int maxDim = 1600;
        if (bitmap.getWidth() > maxDim || bitmap.getHeight() > maxDim) {
            float scale = Math.min((float) maxDim / bitmap.getWidth(), (float) maxDim / bitmap.getHeight());
            int newW = (int) (bitmap.getWidth() * scale);
            int newH = (int) (bitmap.getHeight() * scale);
            sendBitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true);
        }
        sendBitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        return baos.toByteArray();
    }

    private String callBaiduApi(String baseUrl, String imageBase64, String accessToken) throws Exception {
        String urlStr = baseUrl + "?access_token=" + accessToken;
        String encodedImage = URLEncoder.encode(imageBase64, "UTF-8");

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

    private String combineResults(String objectJson, String ocrJson) {
        StringBuilder sb = new StringBuilder();

        // Parse object recognition results
        sb.append("=== 物体/人物识别 ===\n");
        List<String[]> objects = parseObjectResults(objectJson);
        if (objects.isEmpty()) {
            sb.append("未识别到物体\n");
            if (objectJson.contains("error_code")) {
                sb.append("错误: ").append(extractErrorMsg(objectJson)).append("\n");
            }
        } else {
            for (int i = 0; i < objects.size(); i++) {
                String[] item = objects.get(i);
                String keyword = item[0];
                String score = item[1];
                String root = item[2];
                sb.append(i + 1).append(". ").append(keyword);
                if (!score.isEmpty()) {
                    try {
                        float s = Float.parseFloat(score);
                        sb.append(" (置信度: ").append(String.format("%.1f%%", s * 100)).append(")");
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (!root.isEmpty()) {
                    sb.append("\n   分类: ").append(root);
                }
                sb.append("\n");
            }
        }

        // Parse OCR results
        sb.append("\n=== 文字识别 ===\n");
        List<String> texts = parseOcrResults(ocrJson);
        if (texts.isEmpty()) {
            sb.append("未识别到文字\n");
            if (ocrJson.contains("error_code")) {
                sb.append("错误: ").append(extractErrorMsg(ocrJson)).append("\n");
            }
        } else {
            for (String text : texts) {
                sb.append("- ").append(text).append("\n");
            }
        }

        return sb.toString().trim();
    }

    private List<String[]> parseObjectResults(String json) {
        List<String[]> results = new ArrayList<>();
        try {
            // Find "result":[ array
            int resultIdx = json.indexOf("\"result\"");
            if (resultIdx < 0) return results;

            int arrStart = json.indexOf("[", resultIdx);
            if (arrStart < 0) return results;

            // Parse each object in the array
            int pos = arrStart + 1;
            while (pos < json.length()) {
                int objStart = json.indexOf("{", pos);
                if (objStart < 0) break;

                int objEnd = findMatchingBrace(json, objStart);
                if (objEnd < 0) break;

                String obj = json.substring(objStart, objEnd + 1);

                // Extract keyword
                String keyword = extractJsonString(obj, "keyword");
                // Extract score
                String score = extractJsonNumber(obj, "score");
                // Extract root
                String root = extractJsonString(obj, "root");

                if (keyword != null && !keyword.isEmpty()) {
                    results.add(new String[]{keyword, score != null ? score : "", root != null ? root : ""});
                }

                pos = objEnd + 1;
            }
        } catch (Exception ignored) {
        }
        return results;
    }

    private List<String> parseOcrResults(String json) {
        List<String> results = new ArrayList<>();
        try {
            int wordsIdx = json.indexOf("\"words_result\"");
            if (wordsIdx < 0) return results;

            int arrStart = json.indexOf("[", wordsIdx);
            if (arrStart < 0) return results;

            int pos = arrStart + 1;
            while (pos < json.length()) {
                int objStart = json.indexOf("{", pos);
                if (objStart < 0) break;

                int objEnd = findMatchingBrace(json, objStart);
                if (objEnd < 0) break;

                String obj = json.substring(objStart, objEnd + 1);
                String words = extractJsonString(obj, "words");

                if (words != null && !words.isEmpty()) {
                    results.add(words);
                }

                pos = objEnd + 1;
            }
        } catch (Exception ignored) {
        }
        return results;
    }

    private String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end < 0 || end < start) return null;
        return json.substring(start, end);
    }

    private String extractJsonNumber(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start < 0) return null;
        start += pattern.length();
        // Skip whitespace
        while (start < json.length() && json.charAt(start) == ' ') start++;
        // Check if it's a quoted string number
        if (start < json.length() && json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            if (end < 0) return null;
            return json.substring(start, end);
        }
        // It's a raw number
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || c == ' ' || c == ']') break;
            end++;
        }
        return json.substring(start, end).trim();
    }

    private String extractErrorMsg(String json) {
        String msg = extractJsonString(json, "error_msg");
        return msg != null ? msg : json;
    }

    private int findMatchingBrace(String json, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && inString) {
                i++; // skip escaped char
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    private void autoFillFields(String objectJson, String ocrJson) {
        List<String[]> objects = parseObjectResults(objectJson);
        List<String> texts = parseOcrResults(ocrJson);

        // Auto-fill name with top recognized object
        if (!objects.isEmpty()) {
            String topKeyword = objects.get(0)[0];
            recognizedKeyword = topKeyword;
            etName.setText(topKeyword);
        }

        // Build description from all results
        StringBuilder desc = new StringBuilder();

        if (!objects.isEmpty()) {
            desc.append("识别到 ").append(objects.size()).append(" 个物体/人物");
            for (int i = 0; i < objects.size(); i++) {
                String[] item = objects.get(i);
                desc.append("\n").append(i + 1).append(". ").append(item[0]);
                if (!item[1].isEmpty()) {
                    try {
                        float s = Float.parseFloat(item[1]);
                        desc.append(" (").append(String.format("%.1f%%", s * 100)).append(")");
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (!item[2].isEmpty()) {
                    desc.append(" [").append(item[2]).append("]");
                }
            }
        }

        if (!texts.isEmpty()) {
            if (desc.length() > 0) desc.append("\n\n");
            desc.append("文字内容:\n");
            for (String text : texts) {
                desc.append(text).append("\n");
            }
        }

        if (desc.length() > 0) {
            etDescription.setText(desc.toString());
        }
    }

    private void saveRecord() {
        String name = etName.getText().toString().trim();
        String description = etDescription.getText().toString().trim();

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
                // Ignore
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
