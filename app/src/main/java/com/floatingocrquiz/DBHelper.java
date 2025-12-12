package com.floatingocrquiz;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {

    private static final String TAG = "com.floatingocrquiz.DBHelper";
    private static final String DATABASE_NAME = "question_bank.db";
    private static final int DATABASE_VERSION = 1;

    // 表名
    public static final String TABLE_QUESTIONS = "questions";

    // 列名
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TYPE = "type";
    public static final String COLUMN_QUESTION = "question";
    public static final String COLUMN_OPTIONS = "options";
    public static final String COLUMN_ANSWER = "answer";

    // 创建表的SQL语句
    private static final String CREATE_TABLE_QUESTIONS = "CREATE TABLE " + TABLE_QUESTIONS + "(" +
            COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            COLUMN_TYPE + " TEXT NOT NULL, " +
            COLUMN_QUESTION + " TEXT NOT NULL, " +
            COLUMN_OPTIONS + " TEXT, " +
            COLUMN_ANSWER + " TEXT NOT NULL" +
            ");";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建表
        db.execSQL(CREATE_TABLE_QUESTIONS);
        Log.d(TAG, "数据库表创建成功");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 如果表已存在，先删除
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUESTIONS);
        // 重新创建表
        onCreate(db);
    }

    /**
     * 插入单条题目
     * @param question 题目对象
     * @return 插入的行ID，失败返回-1
     */
    public long insertQuestion(QuestionBankHelper.Question question) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(COLUMN_TYPE, question.type.name());
        values.put(COLUMN_QUESTION, question.question);

        // 将选项列表转换为JSON字符串
        if (question.options != null && !question.options.isEmpty()) {
            JSONArray optionsArray = new JSONArray(question.options);
            values.put(COLUMN_OPTIONS, optionsArray.toString());
        }

        values.put(COLUMN_ANSWER, question.answer);

        long id = db.insert(TABLE_QUESTIONS, null, values);
        db.close();
        return id;
    }

    /**
     * 批量插入题目
     * @param questions 题目列表
     * @return 插入成功的数量
     */
    public int batchInsertQuestions(List<QuestionBankHelper.Question> questions) {
        SQLiteDatabase db = this.getWritableDatabase();
        int successCount = 0;

        try {
            db.beginTransaction();
            for (QuestionBankHelper.Question question : questions) {
                ContentValues values = new ContentValues();
                values.put(COLUMN_TYPE, question.type.name());
                values.put(COLUMN_QUESTION, question.question);
                
                // 将选项列表转换为JSON字符串
                if (question.options != null && !question.options.isEmpty()) {
                    JSONArray optionsArray = new JSONArray(question.options);
                    values.put(COLUMN_OPTIONS, optionsArray.toString());
                }
                
                values.put(COLUMN_ANSWER, question.answer);
                
                long id = db.insert(TABLE_QUESTIONS, null, values);
                if (id != -1) {
                    successCount++;
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "批量插入题目失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            db.endTransaction();
            db.close();
        }

        return successCount;
    }

    /**
     * 获取所有题目
     * @return 题目列表
     */
    public List<QuestionBankHelper.Question> getAllQuestions() {
        List<QuestionBankHelper.Question> questions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.query(TABLE_QUESTIONS, null, null, null, null, null, null);

        if (cursor.moveToFirst()) {
            do {
                QuestionBankHelper.Question question = cursorToQuestion(cursor);
                questions.add(question);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return questions;
    }

    /**
     * 根据ID获取题目
     * @param id 题目ID
     * @return 题目对象，不存在返回null
     */
    public QuestionBankHelper.Question getQuestionById(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        QuestionBankHelper.Question question = null;

        Cursor cursor = db.query(
                TABLE_QUESTIONS,
                null,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        );

        if (cursor.moveToFirst()) {
            question = cursorToQuestion(cursor);
        }

        cursor.close();
        db.close();
        return question;
    }

    /**
     * 搜索题目（根据题干模糊匹配）
     * @param keyword 搜索关键词
     * @return 匹配的题目列表
     */
    public List<QuestionBankHelper.Question> searchQuestions(String keyword) {
        List<QuestionBankHelper.Question> questions = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String selection = COLUMN_QUESTION + " LIKE ?";
        String[] selectionArgs = new String[]{"%" + keyword + "%"};

        Cursor cursor = db.query(
                TABLE_QUESTIONS,
                null,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        if (cursor.moveToFirst()) {
            do {
                QuestionBankHelper.Question question = cursorToQuestion(cursor);
                questions.add(question);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return questions;
    }

    /**
     * 删除所有题目
     * @return 影响的行数
     */
    public int deleteAllQuestions() {
        SQLiteDatabase db = this.getWritableDatabase();
        int rowsDeleted = db.delete(TABLE_QUESTIONS, null, null);
        db.close();
        return rowsDeleted;
    }

    /**
     * 获取题目总数
     * @return 题目总数
     */
    public int getQuestionCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_QUESTIONS, null);
        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();
        db.close();
        return count;
    }

    /**
     * 将Cursor转换为Question对象
     * @param cursor 查询结果游标
     * @return Question对象
     */
    private QuestionBankHelper.Question cursorToQuestion(Cursor cursor) {
        QuestionBankHelper.Question question = new QuestionBankHelper.Question();
        question.id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID));
        question.type = QuestionBankHelper.QuestionType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE)));
        question.question = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_QUESTION));
        question.answer = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ANSWER));

        // 解析选项JSON
        String optionsJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_OPTIONS));
        if (optionsJson != null && !optionsJson.isEmpty()) {
            try {
                JSONArray optionsArray = new JSONArray(optionsJson);
                List<String> options = new ArrayList<>();
                for (int i = 0; i < optionsArray.length(); i++) {
                    options.add(optionsArray.getString(i));
                }
                question.options = options;
            } catch (JSONException e) {
                Log.e(TAG, "解析选项JSON失败: " + e.getMessage());
            }
        }

        return question;
    }
}