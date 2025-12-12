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
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

public class QuestionBankHelper {
    private static final String TAG = "com.floatingocrquiz.QuestionBankHelper";
    private static final String FILE_NAME = "question_bank.json";
    
    private static QuestionBankHelper instance;
    private DBHelper dbHelper;
    private Context context;

    private QuestionBankHelper(Context context) {
        this.context = context;
        this.dbHelper = new DBHelper(context);
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
     * 从assets目录加载JSON格式的题库并导入到数据库
     */
    private void loadQuestionBank() {
        // 检查数据库中是否已有题目
        if (dbHelper.getQuestionCount() > 0) {
            Log.d(TAG, "数据库中已有 " + dbHelper.getQuestionCount() + " 道题目，无需重复导入");
            return;
        }

        try {
            InputStream is = context.getAssets().open(FILE_NAME);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
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
            
            List<Question> tempQuestions = new ArrayList<>();
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
                tempQuestions.add(question);
            }
            
            // 批量插入到数据库
            int insertedCount = dbHelper.batchInsertQuestions(tempQuestions);
            Log.d(TAG, "成功从JSON导入 " + insertedCount + " 道题目到数据库");
            
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
        
        // 清理OCR识别的文本（包含选项，后续根据题型决定是否使用）
        String cleanedQuestion = cleanOCRText(questionText);
        Log.d(TAG, "清理后的完整OCR文本: " + cleanedQuestion);
        
        // 提取关键词
        List<String> keywords = extractKeywords(cleanedQuestion);
        
        // 查找最匹配的问题
        Question bestMatch = findBestMatch(cleanedQuestion, keywords);
        
        if (bestMatch != null) {
            // 提取OCR输入中的选项内容，用于后续按顺序组织选项
            List<String> ocrOptions = extractOptionsFromOCRText(cleanedQuestion);
            return formatAnswer(bestMatch, ocrOptions);
        } else {
            return "题库中未找到相关答案";
        }
    }
    
    /**
     * 从完整的OCR识别文本中提取纯问题内容（忽略选项）
     */
    private String extractPureQuestionContent(String fullText) {
        if (fullText == null || fullText.isEmpty()) return "";
        
        try {
            // 查找选项标记的位置，支持多种格式：
            // 1. 字母+中英文句号：A.、A．、a.、a．
            // 2. 括号+字母+中英文句号：（A）.、(A).、【A】.、[A].
            // 3. 数字+中英文句号：1.、1．
            // 4. 字母+括号：A）、A)、a）、a)
            // 5. 括号+字母：（A）、(A)、【A】、[A]
            Pattern pattern = Pattern.compile(
                "(?:[A-Za-z][。．])|" +  // 字母+中英文句号
                "(?:\\（[A-Za-z]\\）[。．]|\\([A-Za-z]\\)[。．]|\\【[A-Za-z]\\】[。．]|\\[[A-Za-z]\\][。．])|" +  // 括号+字母+中英文句号
                "(?:[0-9][。．])|" +  // 数字+中英文句号
                "(?:[A-Za-z]\\）|[A-Za-z]\\))|" +  // 字母+右括号
                "(?:\\（[A-Za-z]\\）|\\([A-Za-z]\\)|\\【[A-Za-z]\\】|\\[[A-Za-z]\\])",  // 括号+字母
                Pattern.CASE_INSENSITIVE
            );
            
            Matcher matcher = pattern.matcher(fullText);
            
            if (matcher.find()) {
                // 提取选项前的文本作为纯问题内容
                return fullText.substring(0, matcher.start()).trim();
            }
        } catch (Exception e) {
            Log.e(TAG, "提取纯问题内容失败: " + e.getMessage());
        }
        
        // 如果没有找到选项标记或发生异常，返回完整文本
        return fullText;
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
        
        // 统一括号内的空格（去除括号内的所有空格）
        text = text.replaceAll("（\\s*）", "（）");
        
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
    private Question findBestMatch(String cleanedOCRText, List<String> keywords) {
        Question bestMatch = null;
        double highestScore = 0.0;
        
        // 先从OCR文本中提取纯问题内容
        String pureQuestion = extractPureQuestionContent(cleanedOCRText);
        
        // 使用数据库模糊搜索缩小范围，提高效率
        List<Question> candidateQuestions;
        if (pureQuestion.length() > 5) {
            // 提取关键词进行数据库搜索
            String searchKeyword = keywords.isEmpty() ? pureQuestion.substring(0, Math.min(10, pureQuestion.length())) : keywords.get(0);
            candidateQuestions = dbHelper.searchQuestions(searchKeyword);
            Log.d(TAG, "数据库搜索到 " + candidateQuestions.size() + " 道候选题目");
        } else {
            // 对于短文本，直接获取所有题目
            candidateQuestions = dbHelper.getAllQuestions();
            Log.d(TAG, "获取所有 " + candidateQuestions.size() + " 道题目进行匹配");
        }
        
        for (Question question : candidateQuestions) {
            String ocrTextForMatch;
            String bankTextForMatch;
            double optionMatchBonus = 0.0; // 初始化选项匹配奖励
            
            try {
                // 根据题型决定匹配内容
                if (question.type == QuestionType.SINGLE || question.type == QuestionType.MULTIPLE) {
                    // 选择题：包含题干和选项
                    ocrTextForMatch = pureQuestion;
                    
                    // 构建题库题目的题干部分
                    bankTextForMatch = cleanOCRText(question.question);
                    
                    // 提取OCR输入中的选项内容
                    List<String> ocrOptions = extractOptionsFromOCRText(cleanedOCRText);
                    
                    // 计算选项匹配度（不考虑顺序）
                    if (!ocrOptions.isEmpty() && question.options != null && !question.options.isEmpty()) {
                        optionMatchBonus = calculateOptionMatching(ocrOptions, question.options);
                        Log.d(TAG, "Question ID " + question.id + " option match bonus: " + optionMatchBonus);
                    }
                } else {
                    // 判断题、简答题：只包含题干
                    ocrTextForMatch = pureQuestion;
                    bankTextForMatch = cleanOCRText(question.question);
                }
                
                // 跳过空字符串的匹配
                if (ocrTextForMatch.isEmpty() || bankTextForMatch.isEmpty()) {
                    continue;
                }
                
                // 计算相似度分数，选择题增加选项匹配奖励
                double baseScore = calculateSimilarity(ocrTextForMatch, bankTextForMatch, keywords);
                double totalScore = baseScore + optionMatchBonus;
                Log.d(TAG, "Question ID " + question.id + " base score: " + baseScore + ", total score: " + totalScore);
                
                if (totalScore > highestScore) {
                    highestScore = totalScore;
                    bestMatch = question;
                }
            } catch (Exception e) {
                Log.e(TAG, "查找最佳匹配失败: " + e.getMessage());
            }
        }
        
        // 设置匹配阈值（降低阈值以提高匹配率）
        if (highestScore > 0.15) {
            return bestMatch;
        }
        
        return null;
    }
    
    /**
     * 从OCR识别的文本中提取选项内容
     */
    private List<String> extractOptionsFromOCRText(String cleanedOCRText) {
        List<String> options = new ArrayList<>();
        
        // 1. 支持多种选项格式：字母+中英文句号（A.、A．、a.、a．）
        Pattern pattern1 = Pattern.compile("[a-gA-G][。．]\\s*(.+?)(?=[a-gA-G][。．]|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher1 = pattern1.matcher(cleanedOCRText);
        while (matcher1.find()) {
            options.add(matcher1.group(1).trim());
        }
        
        // 如果找到选项，返回结果
        if (!options.isEmpty()) {
            return options;
        }
        
        // 2. 支持括号+字母格式：（A）、(A)、【A】、[A]、（a）等
        Pattern pattern2 = Pattern.compile(
            "(?:\\（[a-gA-G]\\）|\\([a-gA-G]\\)|\\【[a-gA-G]\\】|\\[[a-gA-G]\\])\\s*(.+?)(?=(?:\\（[a-gA-G]\\）|\\([a-gA-G]\\)|\\【[a-gA-G]\\】|\\[[a-gA-G]\\])|$)", 
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher matcher2 = pattern2.matcher(cleanedOCRText);
        while (matcher2.find()) {
            options.add(matcher2.group(1).trim());
        }
        
        // 如果找到选项，返回结果
        if (!options.isEmpty()) {
            return options;
        }
        
        // 3. 支持数字序号格式：1.、2.、3.、1．等
        Pattern pattern3 = Pattern.compile("[1-7][。．]\\s*(.+?)(?=[1-9][。．]|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher3 = pattern3.matcher(cleanedOCRText);
        while (matcher3.find()) {
            options.add(matcher3.group(1).trim());
        }
        
        // 如果找到选项，返回结果
        if (!options.isEmpty()) {
            return options;
        }
        
        // 4. 支持小写字母+右括号格式：a）、a)、b）、b)等
        Pattern pattern4 = Pattern.compile("[a-g][）)]\\s*(.+?)(?=[a-g][）)]|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher4 = pattern4.matcher(cleanedOCRText);
        while (matcher4.find()) {
            options.add(matcher4.group(1).trim());
        }
        
        // 如果找到选项，返回结果
        if (!options.isEmpty()) {
            return options;
        }
        
        // 5. 支持括号+字母+句号格式：（A）.、(A).、【A】.、[A].等
        Pattern pattern5 = Pattern.compile(
            "(?:\\（[a-gA-G]\\）[。．]|\\([a-gA-G]\\)[。．]|\\【[a-gA-G]\\】[。．]|\\[[a-gA-G]\\][。．])\\s*(.+?)(?=(?:\\（[a-gA-G]\\）[。．]|\\([a-gA-G]\\)[。．]|\\【[a-gA-G]\\】[。．]|\\[[a-gA-G]\\][。．])|$)", 
            Pattern.DOTALL | Pattern.CASE_INSENSITIVE
        );
        Matcher matcher5 = pattern5.matcher(cleanedOCRText);
        while (matcher5.find()) {
            options.add(matcher5.group(1).trim());
        }
        
        return options;
    }
    
    /**
     * 计算OCR识别的选项与题库选项的匹配度（不考虑顺序）
     */
    private double calculateOptionMatching(List<String> ocrOptions, List<String> bankOptions) {
        // 计算匹配的选项数量
        int matchedCount = 0;
        List<String> cleanedBankOptions = new ArrayList<>();
        
        // 预处理题库选项
        for (String option : bankOptions) {
            cleanedBankOptions.add(cleanOCRText(option));
        }
        
        for (String ocrOption : ocrOptions) {
            String cleanedOcrOption = cleanOCRText(ocrOption);
            for (String bankOption : cleanedBankOptions) {
                // 使用相似度匹配，提高容错率
                if (calculateSimilarity(cleanedOcrOption, bankOption, new ArrayList<>()) > 0.9) {
                    matchedCount++;
                    break;
                }
            }
        }
        
        // 计算匹配度（最多贡献0.3的分数）
        int totalOptions = Math.max(ocrOptions.size(), bankOptions.size());
        if (totalOptions == 0) {
            return 0.0;
        }
        
        return (double) matchedCount / totalOptions * 0.3; // 选项匹配度最高贡献0.3分
    }

    /**
     * 计算问题相似度
     */
    private double calculateSimilarity(String text1, String text2, List<String> keywords) {
        // 如果两个文本完全相同，直接返回1.0
        if (text1.equals(text2)) {
            return 1.0;
        }
        
        // 文本预处理
        String processedText1 = preprocessForSimilarity(text1);
        String processedText2 = preprocessForSimilarity(text2);
        
        // 如果预处理后文本完全相同，返回1.0
        if (processedText1.equals(processedText2)) {
            return 1.0;
        }
        
        if (processedText1.isEmpty() || processedText2.isEmpty()) {
            return 0;
        }
        
        // Jaccard相似度
        double jaccardScore = calculateJaccardSimilarity(processedText1, processedText2);
        
        // 关键词匹配得分
        double keywordScore = calculateKeywordScore(processedText1, processedText2, keywords);
        
        // 最长公共子串长度得分
        double lcsScore = calculateLCSScore(processedText1, processedText2);
        
        // 增加内容重叠度检查（对于相似的长文本给予更高权重）
        double overlapScore = calculateOverlapScore(processedText1, processedText2);
        
        // 综合相似度得分（调整加权平均，增加最长公共子串的权重）
        double totalScore = jaccardScore * 0.3 + keywordScore * 0.2 + lcsScore * 0.3 + overlapScore * 0.2;
        
        // 对于短文本（少于5个字符），增加相似度分数的权重
        if (text1.length() < 5 && text2.length() < 5) {
            totalScore = Math.min(1.0, totalScore + 0.2);
        }
        
        return totalScore;
    }
    
    /**
     * 相似度计算前的预处理
     */
    private String preprocessForSimilarity(String text) {
        // 移除常见前缀和后缀
        text = text.replaceAll("^[Qq]:\\s*[A-Z]+\\s*", "");
        text = text.replaceAll("\\s*[Aa]:\\s*[A-Z]+\\s*$", "");
        
        // 移除引导语时保留关键信息
        // 原来的正则表达式 "^依据.*，" 会移除整段引导语，导致重要信息丢失
        // 修改为更保守的预处理方式
        
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
     * 计算内容重叠度得分
     */
    
    /**
     * 从JSON字符串导入题库
     * @param jsonContent JSON格式的题库内容
     * @return 导入成功的题目数量
     */
    public int importQuestionBank(String jsonContent) {
        try {
            JSONObject jsonObject = new JSONObject(jsonContent);
            JSONArray questionsArray = jsonObject.getJSONArray("questions");
            
            List<Question> tempQuestions = new ArrayList<>();
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
                tempQuestions.add(question);
            }
            
            // 批量插入到数据库
            int insertedCount = dbHelper.batchInsertQuestions(tempQuestions);
            Log.d(TAG, "成功从JSON导入 " + insertedCount + " 道题目到数据库");
            return insertedCount;
            
        } catch (JSONException e) {
            Log.e(TAG, "解析JSON失败: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
    
    /**
     * 删除所有题库
     * @return 是否删除成功
     */
    public boolean deleteAllQuestions() {
        int rowsDeleted = dbHelper.deleteAllQuestions();
        Log.d(TAG, "成功删除 " + rowsDeleted + " 道题目");
        return rowsDeleted > 0;
    }
    
    /**
     * 获取数据库中题目数量
     * @return 题目数量
     */
    public int getQuestionCount() {
        return dbHelper.getQuestionCount();
    }
    private double calculateOverlapScore(String text1, String text2) {
        // 检查较短文本是否是较长文本的子串
        if (text1.contains(text2) || text2.contains(text1)) {
            return 1.0;
        }
        
        // 计算两个文本的内容重叠比例
        int overlapCount = 0;
        String longerText = text1.length() > text2.length() ? text1 : text2;
        String shorterText = text1.length() <= text2.length() ? text1 : text2;
        
        // 统计较短文本中出现在较长文本中的字符比例
        for (char c : shorterText.toCharArray()) {
            if (longerText.indexOf(c) != -1) {
                overlapCount++;
            }
        }
        
        return (double) overlapCount / shorterText.length();
    }

    /**
     * 格式化答案，支持按OCR选项顺序重新组织选项
     */
    private String formatAnswer(Question question, List<String> ocrOptions) {
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
        
        // 添加问题（如果太长则智能压缩）
        String compressedQuestion = compressLongText(question.question, 20, 10);
        sb.append("问题: " + compressedQuestion + "\n");
        
        // 添加选项（如果有）
        if (question.options != null && !question.options.isEmpty()) {
            sb.append("选项:\n");
            
            // 获取按OCR选项顺序匹配后的题库选项顺序
            List<String> reorderedOptions = getReorderedOptions(question.options, ocrOptions);
            
            char optionLabel = 'A';
            for (String option : reorderedOptions) {
                // 检查当前选项是否为正确答案
                boolean isCorrect = isOptionCorrect(option, question.options, question.answer);
                
                if (isCorrect) {
                    // 简化标记，只在选项标签前添加[CORRECT]，不再包裹整个选项
                    sb.append(optionLabel + ". [CORRECT]" + option + "\n");
                } else {
                    sb.append(optionLabel + ". " + option + "\n");
                }
                optionLabel++;
            }
        }
        
        // 添加答案
        sb.append("答案: ");
        
        if (question.type == QuestionType.TRUE_FALSE) {
            // 判断题显示完整答案
            sb.append(question.answer.equals("TRUE") ? "正确" : "错误");
        } else if (question.type == QuestionType.SHORT) {
            // 简答题显示完整答案
            sb.append(question.answer);
        } else {
            // // 选择题：只通过红色高亮显示正确选项，不单独显示答案
            // sb.append(question.answer);
            // 
            // // 如果是单选题，显示选项内容
            // if (question.type == QuestionType.SINGLE && question.answer.length() == 1) {
            //     int index = question.answer.charAt(0) - 'A';
            //     if (index >= 0 && index < question.options.size()) {
            //         sb.append(" (" + question.options.get(index) + ")");
            //     }
            // }
            sb.append("请查看红色高亮选项");
        }
        
        return sb.toString();
    }
    
    /**
     * 智能压缩长文本
     * @param text 原始文本
     * @param startKeep 开头保留长度
     * @param endKeep 结尾保留长度
     * @return 压缩后的文本
     */
    private String compressLongText(String text, int startKeep, int endKeep) {
        if (text == null || text.length() <= startKeep + endKeep + 10) {
            return text; // 文本长度适中，不需要压缩
        }
        
        // 调整末尾保留长度为6-7个文字
        endKeep = Math.min(7, Math.max(6, endKeep));
        
        // 查找括号内的内容，保留重要信息
        Pattern bracketPattern = Pattern.compile("[（(\\\\[\\\\{].*?[）)\\\\]\\\\}]");
        Matcher matcher = bracketPattern.matcher(text);
        
        if (matcher.find()) {
            int keyPartStart = matcher.start();
            int keyPartEnd = matcher.end();
            
            // 确保keyPart在文本中间位置
            if (keyPartStart > startKeep && keyPartEnd < text.length() - endKeep) {
                // 保留括号前后的2-3个文字
                int beforeBracket = Math.max(0, keyPartStart - 3);
                int afterBracket = Math.min(text.length(), keyPartEnd + 3);
                
                return text.substring(0, startKeep) + "..." + 
                       text.substring(beforeBracket, afterBracket) + "..." + 
                       text.substring(text.length() - endKeep);
            }
        }
        
        // 查找连续的特殊字符（如下划线），保留前后内容
        Pattern underlinePattern = Pattern.compile("_{3,}");
        matcher = underlinePattern.matcher(text);
        
        if (matcher.find()) {
            int underlineStart = matcher.start();
            int underlineEnd = matcher.end();
            
            // 确保下划线在文本中间位置
            if (underlineStart > startKeep && underlineEnd < text.length() - endKeep) {
                // 保留下划线前后的2-3个文字
                int beforeUnderline = Math.max(0, underlineStart - 3);
                int afterUnderline = Math.min(text.length(), underlineEnd + 3);
                
                return text.substring(0, startKeep) + "..." + 
                       text.substring(beforeUnderline, afterUnderline) + "..." + 
                       text.substring(text.length() - endKeep);
            }
        }
        
        // 默认压缩方式：保留开头和结尾
        return text.substring(0, startKeep) + "..." + text.substring(text.length() - endKeep);
    }
    
    /**
     * 获取按OCR选项顺序重新组织的题库选项
     */
    private List<String> getReorderedOptions(List<String> bankOptions, List<String> ocrOptions) {
        // 如果没有OCR选项或题库选项，直接返回原始顺序
        if (ocrOptions == null || ocrOptions.isEmpty() || bankOptions == null || bankOptions.isEmpty()) {
            Log.d(TAG, "没有OCR选项或题库选项，直接返回原始顺序");
            return new ArrayList<>(bankOptions);
        }
        
        Log.d(TAG, "原始题库选项: " + bankOptions);
        Log.d(TAG, "OCR提取的选项: " + ocrOptions);
        
        // 创建已匹配选项的集合，避免重复添加
        Set<Integer> matchedBankIndices = new HashSet<>();
        // 创建结果列表，用于存储重新排序后的选项
        List<String> reorderedOptions = new ArrayList<>();
        
        // 遍历OCR识别的选项，按照OCR顺序处理
        for (int ocrIndex = 0; ocrIndex < ocrOptions.size(); ocrIndex++) {
            String ocrOption = ocrOptions.get(ocrIndex);
            // 清理OCR选项文本
            String cleanedOcrOption = cleanOCRText(ocrOption);
            
            // 如果OCR选项文本为空，跳过
            if (cleanedOcrOption.isEmpty()) {
                Log.d(TAG, "OCR选项" + ocrIndex + "文本为空，跳过");
                continue;
            }
            
            // 初始化最佳匹配变量
            int bestMatchIndex = -1;
            double highestSimilarity = 0.0;
            
            // 在题库选项中查找最佳匹配
            for (int i = 0; i < bankOptions.size(); i++) {
                // 如果该题库选项已被匹配，跳过
                if (matchedBankIndices.contains(i)) {
                    continue;
                }
                
                // 清理题库选项文本
                String cleanedBankOption = cleanOCRText(bankOptions.get(i));
                
                // 计算相似度
                double similarity = calculateSimilarity(cleanedOcrOption, cleanedBankOption, new ArrayList<>());
                
                Log.d(TAG, "OCR选项" + ocrIndex + "(" + cleanedOcrOption + ") 与题库选项" + i + "(" + cleanedBankOption + ") 的相似度: " + similarity);
                
                // 更新最佳匹配
                if (similarity > highestSimilarity) {
                    highestSimilarity = similarity;
                    bestMatchIndex = i;
                }
            }
            
            // 如果找到最佳匹配，添加到结果列表
            if (bestMatchIndex != -1) {
                Log.d(TAG, "OCR选项" + ocrIndex + "最佳匹配为题库选项" + bestMatchIndex + "，相似度: " + highestSimilarity);
                reorderedOptions.add(bankOptions.get(bestMatchIndex));
                matchedBankIndices.add(bestMatchIndex);
            }
        }
        
        // 添加剩余未匹配的题库选项
        Log.d(TAG, "已匹配的题库选项索引: " + matchedBankIndices);
        for (int i = 0; i < bankOptions.size(); i++) {
            if (!matchedBankIndices.contains(i)) {
                Log.d(TAG, "添加未匹配的题库选项" + i + "到结果列表");
                reorderedOptions.add(bankOptions.get(i));
            }
        }
        
        // 确保结果列表与原始题库选项数量相同
        if (reorderedOptions.size() != bankOptions.size()) {
            Log.d(TAG, "结果列表与原始题库选项数量不同，返回原始顺序");
            return new ArrayList<>(bankOptions);
        }
        
        Log.d(TAG, "重新排序后的选项: " + reorderedOptions);
        return reorderedOptions;
    }
    
    /**
     * 检查指定选项是否为正确答案
     */
    private boolean isOptionCorrect(String option, List<String> bankOptions, String answer) {
        // 遍历原始题库选项，找到匹配的选项
        for (int i = 0; i < bankOptions.size(); i++) {
            String bankOption = bankOptions.get(i);
            // 使用相似度匹配，提高对OCR误差的容忍度
            if (calculateSimilarity(cleanOCRText(option), cleanOCRText(bankOption), new ArrayList<>()) > 0.9) {
                // 将原始索引转换为选项标签（A, B, C...）
                char optionLabel = (char) ('A' + i);
                // 检查该选项标签是否包含在答案中
                return answer.indexOf(optionLabel) != -1;
            }
        }
        
        return false;
    }

    /**
     * 添加问题到题库
     */
    public void addQuestion(Question question) {
        if (question != null) {
            // 使用数据库插入，不需要手动设置ID
            long id = dbHelper.insertQuestion(question);
            if (id != -1) {
                question.id = (int) id;
                Log.d(TAG, "成功添加新问题: " + question.question);
            }
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