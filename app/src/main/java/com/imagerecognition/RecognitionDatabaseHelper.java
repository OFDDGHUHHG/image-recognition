package com.imagerecognition;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class RecognitionDatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "image_recognition.db";
    private static final int DB_VERSION = 1;

    // Records table
    private static final String TABLE_RECORDS = "records";
    static final String COL_ID = "_id";
    static final String COL_NAME = "name";
    static final String COL_DESCRIPTION = "description";
    static final String COL_IMAGE_PATH = "image_path";
    static final String COL_CREATED_AT = "created_at";

    // Training table
    private static final String TABLE_TRAINING = "training";
    static final String COL_TRAIN_ID = "_id";
    static final String COL_TRAIN_LABEL = "label";
    static final String COL_TRAIN_PATH = "path";
    static final String COL_TRAIN_COUNT = "sample_count";

    public RecognitionDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_RECORDS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_NAME + " TEXT NOT NULL, " +
                COL_DESCRIPTION + " TEXT, " +
                COL_IMAGE_PATH + " TEXT, " +
                COL_CREATED_AT + " INTEGER DEFAULT (strftime('%s', 'now'))" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_TRAINING + " (" +
                COL_TRAIN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_TRAIN_LABEL + " TEXT NOT NULL, " +
                COL_TRAIN_PATH + " TEXT NOT NULL" +
                ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_RECORDS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TRAINING);
        onCreate(db);
    }

    // Record operations
    public long insertRecord(String name, String description, String imagePath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME, name);
        values.put(COL_DESCRIPTION, description);
        values.put(COL_IMAGE_PATH, imagePath);
        return db.insert(TABLE_RECORDS, null, values);
    }

    public Cursor getAllRecords() {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_RECORDS, null, null, null, null, null, COL_CREATED_AT + " DESC");
    }

    public Cursor searchRecords(String keyword) {
        SQLiteDatabase db = getReadableDatabase();
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllRecords();
        }
        String selection = COL_NAME + " LIKE ? OR " + COL_DESCRIPTION + " LIKE ?";
        String[] selectionArgs = {"%" + keyword + "%", "%" + keyword + "%"};
        return db.query(TABLE_RECORDS, null, selection, selectionArgs, null, null, COL_CREATED_AT + " DESC");
    }

    public Cursor getRecordById(long id) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_RECORDS, null, COL_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
    }

    public int updateRecord(long id, String name, String description) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_NAME, name);
        values.put(COL_DESCRIPTION, description);
        return db.update(TABLE_RECORDS, values, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    public int deleteRecord(long id) {
        SQLiteDatabase db = getWritableDatabase();
        return db.delete(TABLE_RECORDS, COL_ID + "=?", new String[]{String.valueOf(id)});
    }

    // Training operations
    public long insertTrainSample(String label, String path) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_TRAIN_LABEL, label);
        values.put(COL_TRAIN_PATH, path);
        return db.insert(TABLE_TRAINING, null, values);
    }

    public Cursor getAllTrainLabels() {
        SQLiteDatabase db = getReadableDatabase();
        return db.rawQuery("SELECT " + COL_TRAIN_LABEL + ", COUNT(*) as " + COL_TRAIN_COUNT +
                " FROM " + TABLE_TRAINING + " GROUP BY " + COL_TRAIN_LABEL + " ORDER BY " + COL_TRAIN_LABEL, null);
    }

    public Cursor getTrainSamples(String label) {
        SQLiteDatabase db = getReadableDatabase();
        return db.query(TABLE_TRAINING, null, COL_TRAIN_LABEL + "=?", new String[]{label}, null, null, null);
    }

    public int deleteTrainLabel(long id) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.query(TABLE_TRAINING, new String[]{COL_TRAIN_LABEL}, COL_TRAIN_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null);
        if (cursor.moveToFirst()) {
            String label = cursor.getString(0);
            cursor.close();
            return db.delete(TABLE_TRAINING, COL_TRAIN_LABEL + "=?", new String[]{label});
        }
        cursor.close();
        return 0;
    }
}
