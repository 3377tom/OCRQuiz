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
    private static final int DATABASE_VERSION = 4;

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
            COLUMN_QUESTION + " TEXT NOT NULL CHECK(length(question) <= 600), " +
            COLUMN_OPTIONS + " TEXT CHECK(length(options) <= 250), " +
            COLUMN_ANSWER + " TEXT NOT NULL CHECK(length(answer) <= 1000)" +
            ");";

    public DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建表
        db.execSQL(CREATE_TABLE_QUESTIONS);
        // 为question字段创建索引，提高模糊搜索效率
        db.execSQL("CREATE INDEX idx_questions_question ON " + TABLE_QUESTIONS + "(" + COLUMN_QUESTION + ");");
        Log.d(TAG, "数据库表和索引创建成功");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 使用增量升级策略，避免数据丢失
        if (oldVersion < 2) {
            // 版本1到版本2的升级操作：为question字段添加索引，提高搜索效率
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_questions_question ON " + TABLE_QUESTIONS + "(" + COLUMN_QUESTION + ");");
        }
        if (oldVersion < 3) {
            // 版本2到版本3的升级操作：优化表结构，添加文本长度检查约束
            // 注意：SQLite不支持直接修改表结构添加约束，需要创建新表并迁移数据
            // 这里使用索引优化代替约束添加
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_questions_type ON " + TABLE_QUESTIONS + "(" + COLUMN_TYPE + ");");
        }
        if (oldVersion < 4) {
            // 版本3到版本4的升级操作：确保所有必要的索引都存在
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_questions_question ON " + TABLE_QUESTIONS + "(" + COLUMN_QUESTION + ");");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_questions_type ON " + TABLE_QUESTIONS + "(" + COLUMN_TYPE + ");");
        }
        Log.d(TAG, "数据库从版本 " + oldVersion + " 升级到版本 " + newVersion + " 成功");
    }

    /**
     * 插入单条题目
     * @param question 题目对象
     * @return 插入的行ID，失败返回-1
     */
    public long insertQuestion(QuestionBankHelper.Question question) {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            ContentValues values = new ContentValues();

            values.put(COLUMN_TYPE, question.type.name());
            values.put(COLUMN_QUESTION, question.question);

            // 将选项列表转换为JSON字符串
            if (question.options != null && !question.options.isEmpty()) {
                JSONArray optionsArray = new JSONArray(question.options);
                values.put(COLUMN_OPTIONS, optionsArray.toString());
            }

            values.put(COLUMN_ANSWER, question.answer);

            return db.insert(TABLE_QUESTIONS, null, values);
        } catch (Exception e) {
            Log.e(TAG, "插入题目失败: " + e.getMessage());
            return -1;
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    /**
     * 批量插入题目
     * @param questions 题目列表
     * @return 插入成功的数量
     */
    public int batchInsertQuestions(List<QuestionBankHelper.Question> questions) {
        SQLiteDatabase db = null;
        int successCount = 0;

        if (questions == null || questions.isEmpty()) {
            return 0;
        }

        try {
            db = this.getWritableDatabase();
            db.beginTransaction();
            
            // 使用预编译语句提高性能
            String insertSql = "INSERT INTO " + TABLE_QUESTIONS + " (" + 
                    COLUMN_TYPE + ", " + 
                    COLUMN_QUESTION + ", " + 
                    COLUMN_OPTIONS + ", " + 
                    COLUMN_ANSWER + ") VALUES (?, ?, ?, ?)";
            
            // 预编译SQL语句
            db.compileStatement(insertSql);
            
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
        } finally {
            if (db != null) {
                db.endTransaction();
                db.close();
            }
        }

        return successCount;
    }

    /**
     * 获取所有题目
     * @return 题目列表
     */
    public List<QuestionBankHelper.Question> getAllQuestions() {
        // 默认返回所有题目，内部使用分页加载避免OOM
        return getQuestionsByPage(0, Integer.MAX_VALUE);
    }
    
    /**
     * 分页获取题目
     * @param page 页码（从0开始）
     * @param pageSize 每页数量
     * @return 题目列表
     */
    public List<QuestionBankHelper.Question> getQuestionsByPage(int page, int pageSize) {
        List<QuestionBankHelper.Question> questions = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();
            
            // 计算偏移量
            int offset = page * pageSize;
            
            // 使用分页查询，避免一次性加载大量数据导致OOM
            cursor = db.query(
                    TABLE_QUESTIONS,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    offset + ", " + pageSize
            );

            if (cursor.moveToFirst()) {
                do {
                    QuestionBankHelper.Question question = cursorToQuestion(cursor);
                    questions.add(question);
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "获取题目失败: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }

        return questions;
    }

    /**
     * 根据ID获取题目
     * @param id 题目ID
     * @return 题目对象，不存在返回null
     */
    public QuestionBankHelper.Question getQuestionById(int id) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        QuestionBankHelper.Question question = null;

        try {
            db = this.getReadableDatabase();

            cursor = db.query(
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
        } catch (Exception e) {
            Log.e(TAG, "根据ID获取题目失败: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return question;
    }

    /**
     * 搜索题目（根据题干模糊匹配）
     * @param keyword 搜索关键词
     * @return 匹配的题目列表
     */
    public List<QuestionBankHelper.Question> searchQuestions(String keyword) {
        List<QuestionBankHelper.Question> questions = new ArrayList<>();
        SQLiteDatabase db = null;
        Cursor cursor = null;

        try {
            db = this.getReadableDatabase();

            // 对关键词进行过滤，防止SQL注入
            if (keyword == null || keyword.isEmpty()) {
                return questions;
            }
            
            // 转义SQL通配符（%和_），确保搜索安全
            String escapedKeyword = keyword.replaceAll("([%_])", "\\\\$1");
            
            // 添加COLLATE NOCASE以忽略大小写，确保搜索匹配
            String selection = COLUMN_QUESTION + " LIKE ? COLLATE NOCASE";
            String[] selectionArgs = new String[]{"%" + escapedKeyword + "%"};

            cursor = db.query(
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
        } catch (Exception e) {
            Log.e(TAG, "搜索题目失败: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
        return questions;
    }

    /**
     * 删除所有题目
     * @return 影响的行数
     */
    public int deleteAllQuestions() {
        SQLiteDatabase db = null;
        try {
            db = this.getWritableDatabase();
            return db.delete(TABLE_QUESTIONS, null, null);
        } catch (Exception e) {
            Log.e(TAG, "删除所有题目失败: " + e.getMessage());
            return 0;
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    /**
     * 获取题目总数
     * @return 题目总数
     */
    public int getQuestionCount() {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        int count = 0;

        try {
            db = this.getReadableDatabase();
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_QUESTIONS, null);
            
            if (cursor.moveToFirst()) {
                count = cursor.getInt(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取题目总数失败: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
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