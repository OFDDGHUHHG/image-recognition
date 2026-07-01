package com.imagerecognition;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 100;
    private static final int REQUEST_TAKE_PHOTO = 101;
    private static final int REQUEST_PICK_IMAGE = 102;

    private ListView listHistory;
    private TextView tvEmpty;
    private EditText etSearch;
    private RecognitionDatabaseHelper dbHelper;
    private SimpleCursorAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dbHelper = new RecognitionDatabaseHelper(this);

        listHistory = findViewById(R.id.listHistory);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearch = findViewById(R.id.etSearch);

        setupListView();
        setupSearch();
        loadHistory();
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String keyword = s.toString().trim();
                Cursor cursor = dbHelper.searchRecords(keyword.isEmpty() ? null : keyword);
                adapter.changeCursor(cursor);
                tvEmpty.setVisibility(cursor.getCount() == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupListView() {
        adapter = new SimpleCursorAdapter(
                this,
                R.layout.item_history,
                null,
                new String[]{RecognitionDatabaseHelper.COL_NAME, RecognitionDatabaseHelper.COL_DESCRIPTION, RecognitionDatabaseHelper.COL_CREATED_AT},
                new int[]{R.id.tvName, R.id.tvDescription, R.id.tvDate},
                0
        );
        listHistory.setAdapter(adapter);

        // Click to view detail or edit
        listHistory.setOnItemClickListener((parent, view, position, id) -> {
            Cursor cursor = (Cursor) adapter.getItem(position);
            if (cursor != null) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(RecognitionDatabaseHelper.COL_NAME));
                String desc = cursor.getString(cursor.getColumnIndexOrThrow(RecognitionDatabaseHelper.COL_DESCRIPTION));
                showEditDialog(id, name, desc);
            }
        });

        // Long press to delete
        listHistory.setOnItemLongClickListener((parent, view, position, id) -> {
            Cursor cursor = (Cursor) adapter.getItem(position);
            String name = "";
            if (cursor != null) {
                name = cursor.getString(cursor.getColumnIndexOrThrow(RecognitionDatabaseHelper.COL_NAME));
            }
            new AlertDialog.Builder(this)
                    .setTitle("删除记录")
                    .setMessage("确定要删除「" + name + "」吗？")
                    .setPositiveButton("删除", (d, w) -> {
                        dbHelper.deleteRecord(id);
                        loadHistory();
                        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
    }

    private void showEditDialog(long id, String currentName, String currentDesc) {
        // Build a custom dialog with EditTexts
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 16);

        final EditText etName = new EditText(this);
        etName.setHint("名称");
        etName.setText(currentName);
        etName.setSingleLine(true);
        layout.addView(etName);

        final EditText etDesc = new EditText(this);
        etDesc.setHint("描述");
        etDesc.setText(currentDesc != null ? currentDesc : "");
        etDesc.setMinLines(3);
        etDesc.setGravity(android.view.Gravity.TOP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 16;
        layout.addView(etDesc, lp);

        new AlertDialog.Builder(this)
                .setTitle("编辑记录")
                .setView(layout)
                .setPositiveButton("保存", (d, w) -> {
                    String newName = etName.getText().toString().trim();
                    String newDesc = etDesc.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int rows = dbHelper.updateRecord(id, newName, newDesc);
                    if (rows > 0) {
                        Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show();
                        loadHistory();
                    } else {
                        Toast.makeText(this, "更新失败", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("删除", (d, w) -> {
                    new AlertDialog.Builder(this)
                            .setTitle("确认删除")
                            .setMessage("确定要删除这条记录吗？")
                            .setPositiveButton("删除", (d2, w2) -> {
                                dbHelper.deleteRecord(id);
                                loadHistory();
                                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .show();
    }

    private void loadHistory() {
        Cursor cursor = dbHelper.getAllRecords();
        adapter.changeCursor(cursor);
        tvEmpty.setVisibility(cursor.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    public void onTakePhoto(View view) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        } else {
            takePhoto();
        }
    }

    public void onSelectImage(View view) {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    private void takePhoto() {
        Intent intent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_TAKE_PHOTO);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            takePhoto();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        Bitmap bitmap = null;
        if (requestCode == REQUEST_TAKE_PHOTO && data != null) {
            bitmap = (Bitmap) data.getExtras().get("data");
        } else if (requestCode == REQUEST_PICK_IMAGE && data != null) {
            try {
                Uri uri = data.getData();
                InputStream is = getContentResolver().openInputStream(uri);
                bitmap = BitmapFactory.decodeStream(is);
                is.close();
            } catch (Exception e) {
                Toast.makeText(this, "加载图片失败", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (bitmap != null) {
            startRecognitionActivity(bitmap);
        }
    }

    private void startRecognitionActivity(Bitmap bitmap) {
        try {
            File tempFile = new File(getCacheDir(), "temp_image.jpg");
            FileOutputStream fos = new FileOutputStream(tempFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.close();

            Intent intent = new Intent(this, RecognitionActivity.class);
            intent.putExtra("image_path", tempFile.getAbsolutePath());
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "处理图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.menu_train) {
            startActivity(new Intent(this, TrainingActivity.class));
            return true;
        } else if (id == R.id.menu_about) {
            showAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("拍照识物APP")
                .setMessage("版本：1.1\n\n功能：\n• 拍照/选择图片识别物体\n• 百度识物API（需配置token）\n• 本地训练自定义标签\n• 识别记录保存/搜索/编辑/删除\n\n富二代好牛逼\n微信：L597551791\n更新日期：2025-07-02")
                .setPositiveButton("确定", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }
}
