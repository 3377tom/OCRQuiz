package com.floatingocrquiz;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuestionBankHelper {
    private static final String TAG = "QuestionBankHelper";
    private static final String FILE_NAME = "question_bank.json";
    
    private static QuestionBankHelper instance;
    private List<Question> questionList;
    private Context context;

    private QuestionBankHelper(Context context) {
        this.context = context;
        this.questionList = new ArrayList<>();
        loadQuestionBank();
    }
    
    /**
     * 获取QuestionBankHelper实例（单例模式）
     * @param context 上下文
     * @return QuestionBankHelper实例
     */
    public static synchronized QuestionBankHelper getInstance(Context context) {
        if (instance == null) {
            instance = new QuestionBankHelper(context);
        }
        return instance;
    }

    /**
     * 从assets目录加载JSON格式的题库
     */
    private void loadQuestionBank() {
        try {
            InputStream is = context.getAssets().open(FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            
            reader.close();
            is.close();
            
            // 解析JSON
            JSONObject jsonObject = new JSONObject(sb.toString());
            JSONArray questionsArray = jsonObject.getJSONArray("questions");
            
            for (int i = 0; i < questionsArray.length(); i++) {
                JSONObject questionObj = questionsArray.getJSONObject(i);
                Question question = new Question();
                
                question.id = questionObj.getInt("id");
                question.type = QuestionType.valueOf(questionObj.getString("type"));
                question.question = questionObj.getString("question");
                
                // 解析选项
                if (questionObj.has("options")) {
                    JSONArray optionsArray = questionObj.getJSONArray("options");
                    List<String> options = new ArrayList<>();
                    for (int j = 0; j < optionsArray.length(); j++) {
                        options.add(optionsArray.getString(j));
                    }
                    question.options = options;
                }
                
                question.answer = questionObj.getString("answer");
                questionList.add(question);
            }
            
            Log.d(TAG, "成功加载题库，共 " + questionList.size() + " 道题目");
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "加载题库失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 查询问题的答案
     * @param questionText OCR识别到的问题文本
     * @return 格式化的答案
     */
    public String queryAnswer(String questionText) {
        if (questionText == null || questionText.isEmpty()) {
            return "识别到的问题为空";
        }
        
        // 清理OCR识别的文本
        String cleanedQuestion = cleanOCRText(questionText);
        Log.d(TAG, "清理后的问题文本: " + cleanedQuestion);
        
        // 提取关键词
        List<String> keywords = extractKeywords(cleanedQuestion);
        
        // 查找最匹配的问题
        Question bestMatch = findBestMatch(cleanedQuestion, keywords);
        
        if (bestMatch != null) {
            return formatAnswer(bestMatch);
        } else {
            return "题库中未找到相关答案";
        }
    }

    /**
     * 清理OCR识别的文本，去除噪声
     */
    private String cleanOCRText(String text) {
        if (text == null) {
            return "";
        }
        
        // 处理换行符：将连续的换行符替换为单个空格
        // 这样既保留了文本的基本结构，又避免了换行符导致的匹配问题
        text = text.replaceAll("\\n+", " ");
        
        // 去除多余的空格
        text = text.replaceAll("\\s+", " ").trim();
        
        // 去除常见的OCR错误字符
        text = text.replaceAll("[`~!@#$%^&*()_+\\-=\\[\\]{}|;:\\'\\\"\\\\\\\\,\\.<\\>?/]", "");
        
        // 转换为小写进行匹配
        return text.toLowerCase();
    }

    /**
     * 提取关键词
     */
    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        
        // 简单的关键词提取，去除常见的停用词
        String[] words = text.split(" ");
        String[] stopWords = {"的", "了", "是", "在", "和", "与", "等", "以下", "哪些", "哪个", "不是", "包括"};
        
        for (String word : words) {
            boolean isStopWord = false;
            for (String stopWord : stopWords) {
                if (word.equals(stopWord)) {
                    isStopWord = true;
                    break;
                }
            }
            if (!isStopWord && word.length() > 1) {
                keywords.add(word);
            }
        }
        
        return keywords;
    }

    /**
     * 查找最匹配的问题
     */
    private Question findBestMatch(String cleanedQuestion, List<String> keywords) {
        Question bestMatch = null;
        double highestScore = 0.0;
        
        for (Question question : questionList) {
            String cleanedQ = cleanOCRText(question.question);
            
            // 计算相似度分数
            double score = calculateSimilarity(cleanedQuestion, cleanedQ, keywords);
            
            if (score > highestScore) {
                highestScore = score;
                bestMatch = question;
            }
        }
        
        // 设置匹配阈值
        if (highestScore > 0.3) {
            return bestMatch;
        }
        
        return null;
    }

    /**
     * 计算问题相似度
     */
    private double calculateSimilarity(String text1, String text2, List<String> keywords) {
        // 使用Jaccard相似度
        List<String> words1 = new ArrayList<>(List.of(text1.split(" ")));
        List<String> words2 = new ArrayList<>(List.of(text2.split(" ")));
        
        // 创建交集
        List<String> intersection = new ArrayList<>(words1);
        intersection.retainAll(words2);
        
        // 创建并集
        List<String> union = new ArrayList<>(words1);
        union.addAll(words2);
        
        // 计算Jaccard系数
        double jaccardScore = union.isEmpty() ? 0 : (double) intersection.size() / union.size();
        
        // 关键词匹配加分
        int keywordMatches = 0;
        for (String keyword : keywords) {
            if (text2.contains(keyword)) {
                keywordMatches++;
            }
        }
        
        // 关键词匹配分数
        double keywordScore = keywords.isEmpty() ? 0 : (double) keywordMatches / keywords.size();
        
        // 综合分数
        return jaccardScore * 0.7 + keywordScore * 0.3;
    }

    /**
     * 格式化答案
     */
    private String formatAnswer(Question question) {
        StringBuilder sb = new StringBuilder();
        
        // 添加题目类型
        switch (question.type) {
            case SINGLE:
                sb.append("单选题\n");
                break;
            case MULTIPLE:
                sb.append("多选题\n");
                break;
            case TRUE_FALSE:
                sb.append("判断题\n");
                break;
            case SHORT:
                sb.append("简答题\n");
                break;
        }
        
        // 添加问题
        sb.append("问题: " + question.question + "\n");
        
        // 添加选项（如果有）
        if (question.options != null && !question.options.isEmpty()) {
            sb.append("选项:\n");
            char optionLabel = 'A';
            for (String option : question.options) {
                sb.append(optionLabel++ + ". " + option + "\n");
            }
        }
        
        // 添加答案
        sb.append("答案: ");
        
        if (question.type == QuestionType.TRUE_FALSE) {
            sb.append(question.answer.equals("TRUE") ? "正确" : "错误");
        } else if (question.type == QuestionType.SHORT) {
            sb.append(question.answer);
        } else {
            // 选择题
            sb.append(question.answer);
            
            // 如果是单选题，显示选项内容
            if (question.type == QuestionType.SINGLE && question.answer.length() == 1) {
                int index = question.answer.charAt(0) - 'A';
                if (index >= 0 && index < question.options.size()) {
                    sb.append(" (" + question.options.get(index) + ")");
                }
            }
        }
        
        return sb.toString();
    }

    /**
     * 添加问题到题库
     */
    public void addQuestion(Question question) {
        if (question != null) {
            question.id = questionList.size() + 1;
            questionList.add(question);
            Log.d(TAG, "成功添加新问题: " + question.question);
        }
    }

    /**
     * 题目类型枚举
     */
    public enum QuestionType {
        SINGLE,     // 单选题
        MULTIPLE,   // 多选题
        TRUE_FALSE, // 判断题
        SHORT       // 简答题
    }

    /**
     * 问题数据结构
     */
    public static class Question {
        public int id;
        public QuestionType type;
        public String question;
        public List<String> options;
        public String answer;
    }
}