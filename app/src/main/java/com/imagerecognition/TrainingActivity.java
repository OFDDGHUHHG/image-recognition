package com.imagerecognition;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class TrainingActivity extends AppCompatActivity {

    private static final int REQUEST_TRAIN_IMAGE = 200;

    private ListView listTrainItems;
    private EditText etLabel;
    private TextView tvEmpty;
    private RecognitionDatabaseHelper dbHelper;
    private SimpleCursorAdapter adapter;
    private String currentImagePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_training);

        dbHelper = new RecognitionDatabaseHelper(this);

        listTrainItems = findViewById(R.id.listTrainItems);
        etLabel = findViewById(R.id.etLabel);
        tvEmpty = findViewById(R.id.tvEmpty);

        setupListView();
        loadTrainingData();
    }

    private void setupListView() {
        adapter = new SimpleCursorAdapter(
                this,
                R.layout.item_train,
                null,
                new String[]{RecognitionDatabaseHelper.COL_TRAIN_LABEL, RecognitionDatabaseHelper.COL_TRAIN_COUNT},
                new int[]{R.id.tvLabel, R.id.tvCount},
                0
        );
        listTrainItems.setAdapter(adapter);

        listTrainItems.setOnItemClickListener((parent, view, position, id) -> {
            Cursor cursor = (Cursor) adapter.getItem(position);
            if (cursor != null) {
                String label = cursor.getString(cursor.getColumnIndexOrThrow(RecognitionDatabaseHelper.COL_TRAIN_LABEL));
                showTrainDetailDialog(label);
            }
        });

        listTrainItems.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle("删除标签")
                    .setMessage("确定要删除这个训练标签及其所有样本吗？")
                    .setPositiveButton("删除", (d, w) -> {
                        dbHelper.deleteTrainLabel(id);
                        loadTrainingData();
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }

    private void loadTrainingData() {
        Cursor cursor = dbHelper.getAllTrainLabels();
        adapter.changeCursor(cursor);
        tvEmpty.setVisibility(cursor.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    public void onAddTrainImage(View view) {
        String label = etLabel.getText().toString().trim();
        if (label.isEmpty()) {
            Toast.makeText(this, "请输入标签名称", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_TRAIN_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQUEST_TRAIN_IMAGE) {
            try {
                android.net.Uri uri = data.getData();
                java.io.InputStream is = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();

                // Save image to training directory
                java.io.File trainDir = new java.io.File(getFilesDir(), "training");
                if (!trainDir.exists()) trainDir.mkdirs();
                
                String label = etLabel.getText().toString().trim();
                String filename = System.currentTimeMillis() + ".jpg";
                java.io.File imageFile = new java.io.File(trainDir, filename);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(imageFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.close();

                // Save to database
                dbHelper.insertTrainSample(label, imageFile.getAbsolutePath());
                
                Toast.makeText(this, "已添加训练样本：" + label, Toast.LENGTH_SHORT).show();
                etLabel.setText("");
                loadTrainingData();
            } catch (Exception e) {
                Toast.makeText(this, "添加失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showTrainDetailDialog(String label) {
        Cursor samples = dbHelper.getTrainSamples(label);
        StringBuilder sb = new StringBuilder();
        sb.append("标签：").append(label).append("\n\n");
        sb.append("样本数量：").append(samples.getCount()).append("\n\n");
        
        int count = 0;
        while (samples.moveToNext() && count < 5) {
            String path = samples.getString(samples.getColumnIndexOrThrow(RecognitionDatabaseHelper.COL_TRAIN_PATH));
            sb.append("样本 ").append(count + 1).append("\n");
            count++;
        }
        samples.close();

        new AlertDialog.Builder(this)
                .setTitle("训练详情")
                .setMessage(sb.toString())
                .setPositiveButton("确定", null)
                .show();
    }
}
