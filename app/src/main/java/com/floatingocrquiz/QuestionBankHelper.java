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
        
        // 统一括号格式（转换为中文括号）
        text = text.replaceAll("\\(", "（")
                   .replaceAll("\\)", "）")
                   .replaceAll("\\[", "【")
                   .replaceAll("\\]", "】");
        
        // 统一标点符号格式
        text = text.replaceAll(";", "；")
                   .replaceAll("\\.", "。")
                   .replaceAll(",", "，")
                   .replaceAll("!", "！")
                   .replaceAll("\\?", "？");
        
        // 去除多余的标点符号
        text = text.replaceAll("[。，；？！]+", "。");
        
        // 转换为小写进行匹配
        return text.toLowerCase();
    }

    /**
     * 提取关键词
     * 针对不同题型调整停用词策略，特别是判断题保留关键语义词
     */
    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        
        // 简单的关键词提取
        String[] words = text.split(" ");
        
        // 基础停用词列表（不包含会影响语义的词）
        String[] baseStopWords = {
            "的", "了", "在", "和", "与", "等", "以下", "哪些", "哪个", 
            "包括", "依据", "根据", "按照", "关于", "对", "的话"
        };
        
        // 可能影响语义的词列表（在特定题型中需要保留）
        String[] semanticWords = {
            "不是", "必须", "应当", "应该", "能够", "需要", "可以"
        };
        
        for (String word : words) {
            boolean isStopWord = false;
            
            // 检查是否为基础停用词
            for (String stopWord : baseStopWords) {
                if (word.equals(stopWord)) {
                    isStopWord = true;
                    break;
                }
            }
            
            // 不将语义关键的词作为停用词
            if (!isStopWord && word.length() > 1) {
                keywords.add(word);
            }
        }
        
        // 如果没有提取到关键词，返回文本本身作为关键词
        if (keywords.isEmpty()) {
            keywords.add(text);
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
        
        // 设置匹配阈值（降低阈值以提高匹配率）
        if (highestScore > 0.2) {
            return bestMatch;
        }
        
        return null;
    }

    /**
     * 计算问题相似度
     */
    private double calculateSimilarity(String text1, String text2, List<String> keywords) {
        // 文本预处理
        String processedText1 = preprocessForSimilarity(text1);
        String processedText2 = preprocessForSimilarity(text2);
        
        if (processedText1.isEmpty() || processedText2.isEmpty()) {
            return 0;
        }
        
        // Jaccard相似度
        double jaccardScore = calculateJaccardSimilarity(processedText1, processedText2);
        
        // 关键词匹配得分
        double keywordScore = calculateKeywordScore(processedText1, processedText2, keywords);
        
        // 最长公共子串长度得分
        double lcsScore = calculateLCSScore(processedText1, processedText2);
        
        // 综合相似度得分（加权平均）
        double totalScore = jaccardScore * 0.4 + keywordScore * 0.3 + lcsScore * 0.3;
        
        return totalScore;
    }
    
    /**
     * 相似度计算前的预处理
     */
    private String preprocessForSimilarity(String text) {
        // 移除常见前缀和后缀
        text = text.replaceAll("^[Qq]:\\s*[A-Z]+\\s*", "");
        text = text.replaceAll("\\s*[Aa]:\\s*[A-Z]+\\s*$", "");
        
        // 移除引导语
        text = text.replaceAll("^依据.*，", "");
        text = text.replaceAll("^根据.*，", "");
        
        return text;
    }
    
    /**
     * 计算Jaccard相似度
     */
    private double calculateJaccardSimilarity(String text1, String text2) {
        List<String> words1 = new ArrayList<>(List.of(text1.split(" ")));
        List<String> words2 = new ArrayList<>(List.of(text2.split(" ")));
        
        List<String> intersection = new ArrayList<>(words1);
        intersection.retainAll(words2);
        
        List<String> union = new ArrayList<>(words1);
        union.addAll(words2);
        
        if (union.isEmpty()) {
            return 0;
        }
        
        return (double) intersection.size() / union.size();
    }
    
    /**
     * 计算关键词匹配得分
     */
    private double calculateKeywordScore(String text1, String text2, List<String> keywords) {
        if (keywords.isEmpty()) {
            return 0;
        }
        
        int matchedKeywords = 0;
        for (String keyword : keywords) {
            if (text2.contains(keyword)) {
                matchedKeywords++;
            }
        }
        
        return (double) matchedKeywords / keywords.size();
    }
    
    /**
     * 计算最长公共子串长度得分
     */
    private double calculateLCSScore(String text1, String text2) {
        int m = text1.length();
        int n = text2.length();
        
        int[][] dp = new int[m + 1][n + 1];
        int maxLength = 0;
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    maxLength = Math.max(maxLength, dp[i][j]);
                } else {
                    dp[i][j] = 0;
                }
            }
        }
        
        if (m == 0 || n == 0) {
            return 0;
        }
        
        // 归一化得分
        return (double) maxLength / Math.max(m, n);
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