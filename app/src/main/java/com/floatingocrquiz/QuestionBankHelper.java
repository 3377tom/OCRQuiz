package com.floatingocrquiz;

import android.content.Context;
import android.content.SharedPreferences;
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
                
                // 输入验证：检查内容长度
                boolean isValid = true;
                
                // 检查题干长度（最大600字符）
                if (question.question != null && question.question.length() > 600) {
                    Log.w(TAG, "题目题干过长 (ID: " + question.id + ", Length: " + question.question.length() + ")，已跳过");
                    isValid = false;
                }
                
                // 检查选项长度（转换为JSON字符串后最大250字符）
                if (isValid && question.options != null) {
                    String optionsJson = question.options.toString();
                    if (optionsJson.length() > 250) {
                        Log.w(TAG, "题目选项过长 (ID: " + question.id + ", Length: " + optionsJson.length() + ")，已跳过");
                        isValid = false;
                    }
                }
                
                // 检查答案长度（最大1000字符）
                if (isValid && question.answer != null && question.answer.length() > 1000) {
                    Log.w(TAG, "题目答案过长 (ID: " + question.id + ", Length: " + question.answer.length() + ")，已跳过");
                    isValid = false;
                }
                
                if (isValid) {
                    tempQuestions.add(question);
                }
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
        
        // 打印原始OCR识别的字符
        Log.d(TAG, "原始OCR识别字符: " + questionText);
        
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
     * 提取核心实词，支持按长度和停用词过滤
     */
    private List<String> extractKeywords(String text) {
        List<String> keywords = new ArrayList<>();
        
        // 扩展停用词列表
        Set<String> stopWords = new HashSet<>(Arrays.asList(
            "的", "了", "在",  "等", "以下", "哪些", "哪个", 
            "包括", "依据", "根据", "按照", "关于", "对", "的话", "是", "有", 
            "这", "那", "为", "以", "之", "来", "去", "也", "又", "还", "都", 
            "则", "而",  "就", "但", "却", "并", "且", "及", "于", "由", 
            "至", "从", "向", "到", "被", "把", "将", "让", "使", "令", "给",
            "吗", "呢", "吧", "啊", "呀", "啦", "唉", "哦"
        ));
        
        // 语义关键的词列表（需要保留）
        Set<String> semanticWords = new HashSet<>(Arrays.asList(
            "不是", "必须", "应当", "应该", "能够", "需要", "可以", "禁止", 
            "不得", "允许"
        ));
        
        // 中文分词处理（简单的基于标点和空格的分词）
        // 首先将文本分割为句子
        String[] sentences = text.split("[。，；？！、]");
        
        for (String sentence : sentences) {
            // 进一步分割为词语（基于空格、数字、字母等）
            String[] tokens = sentence.split("[^\u4e00-\u9fa5a-zA-Z]+");
            
            for (String token : tokens) {
                // 清理空字符串
                token = token.trim();
                if (token.isEmpty()) {
                    continue;
                }
                
                // 跳过停用词，但保留语义关键词
                if (!semanticWords.contains(token) && stopWords.contains(token)) {
                    continue;
                }
                
                // 只保留长度超过3个字符的实词
                if (token.length() > 3) {
                    keywords.add(token);
                }
            }
        }
        
        // 如果没有提取到足够的关键词，尝试使用原始文本的一部分
        if (keywords.isEmpty() && text.length() > 5) {
            // 提取文本的前几个字符作为关键词
            keywords.add(text.substring(0, Math.min(8, text.length())));
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
        Log.d(TAG, "提取的纯问题内容: " + pureQuestion);
        
        // 提取选项并打印日志
        List<String> extractedOptions = extractOptionsFromOCRText(cleanedOCRText);
        Log.d(TAG, "提取的选项列表: " + extractedOptions);
        
        // 优化关键词提取
        List<String> coreKeywords = new ArrayList<>();
        if (keywords.size() > 0) {
            // 过滤出长度超过3个字符的关键词
            for (String keyword : keywords) {
                if (keyword.length() > 3) {
                    coreKeywords.add(keyword);
                }
            }
            
            // 如果核心关键词不足3个，从纯问题中提取更多
            if (coreKeywords.size() < 3) {
                List<String> additionalKeywords = extractKeywords(pureQuestion);
                for (String keyword : additionalKeywords) {
                    if (keyword.length() > 3 && !coreKeywords.contains(keyword)) {
                        coreKeywords.add(keyword);
                        if (coreKeywords.size() >= 10) {
                            break;
                        }
                    }
                }
            }
            
            Log.d(TAG, "核心关键词列表: " + coreKeywords);
        }
        
        // 使用数据库模糊搜索缩小范围，提高效率
        List<Question> candidateQuestions = new ArrayList<>();
        
        // 步骤1: 使用多关键词进行数据库粗筛
        if (pureQuestion.length() > 5 && !coreKeywords.isEmpty()) {
            // 随机选择3-5个关键词（最多使用前10个中的关键词）
            List<String> selectedKeywords = new ArrayList<>();
            int maxKeywordsToUse = Math.min(10, coreKeywords.size());
            int numKeywords = Math.max(3, Math.min(5, maxKeywordsToUse));
            
            // 随机选择3-5个关键词
            // 首先获取前10个关键词作为候选池
            List<String> keywordPool = new ArrayList<>();
            for (int i = 0; i < Math.min(10, coreKeywords.size()); i++) {
                keywordPool.add(coreKeywords.get(i));
            }
            
            // 随机打乱候选池
            java.util.Collections.shuffle(keywordPool);
            
            // 选择前numKeywords个关键词
            for (int i = 0; i < Math.min(numKeywords, keywordPool.size()); i++) {
                selectedKeywords.add(keywordPool.get(i));
            }
            
            Log.d(TAG, "选择的搜索关键词: " + selectedKeywords);
            
            // 使用多关键词进行数据库搜索
            for (String keyword : selectedKeywords) {
                List<Question> temp = dbHelper.searchQuestions(keyword);
                // 合并结果，去重
                for (Question q : temp) {
                    if (!candidateQuestions.contains(q)) {
                        candidateQuestions.add(q);
                    }
                }
                
                // 如果已经找到足够多的候选题目，可以提前停止
                if (candidateQuestions.size() > 100) {
                    break;
                }
            }
            
            Log.d(TAG, "多关键词搜索到 " + candidateQuestions.size() + " 道候选题目");
            
            // 步骤2: 如果多关键词搜索结果为空，尝试单关键词搜索
            if (candidateQuestions.isEmpty() && !coreKeywords.isEmpty()) {
                Log.d(TAG, "多关键词搜索结果为空，尝试使用第一个关键词搜索");
                candidateQuestions = dbHelper.searchQuestions(coreKeywords.get(0));
                Log.d(TAG, "单关键词搜索到 " + candidateQuestions.size() + " 道候选题目");
            }
        }
        
        // 步骤3: 兜底，如果搜索结果为空，获取所有题目
        if (candidateQuestions.isEmpty()) {
            Log.d(TAG, "搜索结果为空，获取所有题目进行匹配");
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
        
        // 4. 支持字母+右括号格式：A）、A)、a）、a)
        Pattern pattern4 = Pattern.compile("[a-gA-G][）)]\\s*(.+?)(?=[a-gA-G][）)]|$)", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
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
     * 计算选项匹配度
     */
    private double calculateOptionMatching(List<String> ocrOptions, List<String> bankOptions) {
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
        if (question.options != null && !question.options.isEmpty() && 
            question.type != QuestionType.SHORT) { // 简答题不显示选项
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
            // 判断题：根据重新排序后的选项生成正确答案
            List<String> reorderedOptions = getReorderedOptions(question.options, ocrOptions);
            boolean foundCorrectOption = false;
            
            for (String option : reorderedOptions) {
                boolean isCorrect = isOptionCorrect(option, question.options, question.answer);
                if (isCorrect) {
                    // 检查正确选项的内容，确定显示的图标
                    String cleanedOption = cleanOCRText(option);
                    boolean shouldBeTrue = cleanedOption.contains("正确") || 
                                          cleanedOption.equalsIgnoreCase("正确") || 
                                          cleanedOption.contains("对") ||
                                          cleanedOption.equalsIgnoreCase("对") ||
                                          cleanedOption.contains("真") ||
                                          cleanedOption.equalsIgnoreCase("真") ||
                                          cleanedOption.contains("是") ||
                                          cleanedOption.equalsIgnoreCase("是") ||
                                          cleanedOption.contains("√") ||
                                          cleanedOption.contains("✓") ||
                                          cleanedOption.contains("✔") ||
                                          cleanedOption.contains("✅") ||
                                          cleanedOption.contains("🌕") ||
                                          cleanedOption.contains("✓") ||
                                          cleanedOption.contains("T") ||
                                          cleanedOption.equalsIgnoreCase("T") ||
                                          cleanedOption.contains("Yes") ||
                                          cleanedOption.equalsIgnoreCase("Yes") ||
                                          cleanedOption.contains("Y") ||
                                          cleanedOption.equalsIgnoreCase("Y");
                    
                    if (shouldBeTrue) {
                        sb.append("✅");
                    } else {
                        sb.append("❌");
                    }
                    foundCorrectOption = true;
                    break;
                }
            }
            
            if (!foundCorrectOption) {
                // 如果没有找到正确选项，检查答案是否为选项字母（如"A"）
                boolean isAnswerOptionLetter = false;
                for (char c = 'A'; c <= 'Z'; c++) {
                    if (question.answer.equals(String.valueOf(c))) {
                        isAnswerOptionLetter = true;
                        break;
                    }
                }
                
                if (isAnswerOptionLetter) {
                    // 答案为选项字母，查找对应的选项内容
                    List<String> originalOptions = question.options;
                    if (originalOptions != null && !originalOptions.isEmpty()) {
                        // 将选项字母转换为索引
                        int answerIndex = question.answer.charAt(0) - 'A';
                        if (answerIndex >= 0 && answerIndex < originalOptions.size()) {
                            String answerOption = originalOptions.get(answerIndex);
                            String cleanedOption = cleanOCRText(answerOption);
                            boolean shouldBeTrue = cleanedOption.contains("正确") || 
                                                  cleanedOption.equalsIgnoreCase("正确") || 
                                                  cleanedOption.contains("对") ||
                                                  cleanedOption.equalsIgnoreCase("对") ||
                                                  cleanedOption.contains("真") ||
                                                  cleanedOption.equalsIgnoreCase("真") ||
                                                  cleanedOption.contains("是") ||
                                                  cleanedOption.equalsIgnoreCase("是") ||
                                                  cleanedOption.contains("√") ||
                                                  cleanedOption.contains("✓") ||
                                                  cleanedOption.contains("✔") ||
                                                  cleanedOption.contains("✅") ||
                                                  cleanedOption.contains("🌕") ||
                                                  cleanedOption.contains("✓") ||
                                                  cleanedOption.contains("T") ||
                                                  cleanedOption.equalsIgnoreCase("T") ||
                                                  cleanedOption.contains("Yes") ||
                                                  cleanedOption.equalsIgnoreCase("Yes") ||
                                                  cleanedOption.contains("Y") ||
                                                  cleanedOption.equalsIgnoreCase("Y");
                            
                            if (shouldBeTrue) {
                                sb.append("✅");
                            } else {
                                sb.append("❌");
                            }
                        } else {
                            // 索引无效，显示原始答案
                            sb.append(question.answer);
                        }
                    } else {
                        // 没有选项，显示原始答案
                        sb.append(question.answer);
                    }
                } else {
                    // 回退到原始逻辑，使用忽略大小写比较
                    sb.append(question.answer.equalsIgnoreCase("TRUE") ? "✅" : "❌");
                }
            }
        } else if (question.type == QuestionType.SHORT) {
            // 简答题显示完整答案
            sb.append(question.answer);
        } else {
            // 选择题：根据重新排序后的选项生成正确答案
            StringBuilder answerBuilder = new StringBuilder();
            
            // 获取按OCR选项顺序重新组织的题库选项
            List<String> reorderedOptions = getReorderedOptions(question.options, ocrOptions);
            
            char optionLabel = 'A';
            boolean hasCorrectAnswer = false;
            
            // 遍历重新排序后的选项，找出所有正确答案
            for (int i = 0; i < reorderedOptions.size(); i++) {
                String option = reorderedOptions.get(i);
                boolean isCorrect = isOptionCorrect(option, question.options, question.answer);
                
                if (isCorrect) {
                    if (hasCorrectAnswer) {
                        answerBuilder.append("、"); // 添加选项分隔符
                    }
                    answerBuilder.append(optionLabel); // 添加正确选项标签
                    hasCorrectAnswer = true;
                }
                optionLabel++;
            }
            
            if (hasCorrectAnswer) {
                sb.append(answerBuilder.toString());
            } else {
                sb.append("请查看红色高亮选项");
            }
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
        // 从SharedPreferences获取题干字数限制设置
        SharedPreferences sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        int questionLengthLimit = sharedPreferences.getInt("question_length_limit", 50); // 默认50字
        
        // 如果设置了无限制（0）或者文本长度不超过限制，直接返回完整文本
        if (questionLengthLimit == 0 || text == null || text.length() <= questionLengthLimit) {
            return text;
        }
        
        // 根据字数限制调整保留长度
        // 总长度包括省略号 "..."（3个字符）
        int ellipsisLength = 3;
        int availableLength = questionLengthLimit - ellipsisLength;
        
        // 计算实际需要保留的开头和结尾长度
        // 保证结尾至少保留6-7个文字
        int minEndKeep = Math.min(7, Math.max(6, endKeep));
        int actualEndKeep = Math.min(minEndKeep, availableLength - 10); // 开头至少保留10个字符
        int actualStartKeep = availableLength - actualEndKeep;
        
        // 如果计算出的开头保留长度小于10，调整比例
        if (actualStartKeep < 10) {
            actualStartKeep = 10;
            actualEndKeep = Math.min(availableLength - actualStartKeep, minEndKeep);
        }
        
        // 查找括号内的内容，保留重要信息
        Pattern bracketPattern = Pattern.compile("[（(\\\\[\\\\{].*?[）)\\\\]\\\\}]");
        Matcher matcher = bracketPattern.matcher(text);
        
        if (matcher.find()) {
            int keyPartStart = matcher.start();
            int keyPartEnd = matcher.end();
            
            // 确保keyPart在文本中间位置
            if (keyPartStart > actualStartKeep && keyPartEnd < text.length() - actualEndKeep) {
                // 计算括号内容的长度
                int keyPartLength = keyPartEnd - keyPartStart;
                
                // 根据可用长度调整保留的括号前后内容
                int totalKeepLength = actualStartKeep + keyPartLength + actualEndKeep;
                int extraLength = totalKeepLength - availableLength;
                
                // 如果总长度超过限制，适当减少开头或结尾保留长度
                if (extraLength > 0) {
                    if (actualStartKeep > actualEndKeep) {
                        actualStartKeep -= extraLength;
                        if (actualStartKeep < 5) actualStartKeep = 5;
                    } else {
                        actualEndKeep -= extraLength;
                        if (actualEndKeep < 5) actualEndKeep = 5;
                    }
                }
                
                return text.substring(0, actualStartKeep) + "..." + 
                       text.substring(keyPartStart, keyPartEnd) + "..." + 
                       text.substring(text.length() - actualEndKeep);
            }
        }
        
        // 查找连续的特殊字符（如下划线），保留前后内容
        Pattern underlinePattern = Pattern.compile("_{3,}");
        matcher = underlinePattern.matcher(text);
        
        if (matcher.find()) {
            int underlineStart = matcher.start();
            int underlineEnd = matcher.end();
            
            // 确保下划线在文本中间位置
            if (underlineStart > actualStartKeep && underlineEnd < text.length() - actualEndKeep) {
                // 保留下划线前后的重要内容
                int beforeUnderline = Math.max(0, underlineStart - 2);
                int afterUnderline = Math.min(text.length(), underlineEnd + 2);
                
                return text.substring(0, actualStartKeep) + "..." + 
                       text.substring(beforeUnderline, afterUnderline) + "..." + 
                       text.substring(text.length() - actualEndKeep);
            }
        }
        
        // 默认压缩方式：保留开头和结尾，根据字数限制调整
        return text.substring(0, actualStartKeep) + "..." + text.substring(text.length() - actualEndKeep);
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
                // 对于判断题，特殊处理：直接比较选项内容与答案的对应关系
                if (answer.equalsIgnoreCase("TRUE") || answer.equalsIgnoreCase("FALSE")) {
                    // 判断题答案格式为TRUE/FALSE，检查选项内容
                    String cleanedOption = cleanOCRText(option);
                    
                    // 判断选项内容对应的正确答案
                    boolean shouldBeTrue = cleanedOption.contains("正确") || 
                                          cleanedOption.equalsIgnoreCase("正确") || 
                                          cleanedOption.contains("对") ||
                                          cleanedOption.equalsIgnoreCase("对") ||
                                          cleanedOption.contains("真") ||
                                          cleanedOption.equalsIgnoreCase("真") ||
                                          cleanedOption.contains("是") ||
                                          cleanedOption.equalsIgnoreCase("是") ||
                                          cleanedOption.contains("√") ||
                                          cleanedOption.contains("✓") ||
                                          cleanedOption.contains("✔") ||
                                          cleanedOption.contains("✅") ||
                                          cleanedOption.contains("🌕") ||
                                          cleanedOption.contains("✓") ||
                                          cleanedOption.contains("T") ||
                                          cleanedOption.equalsIgnoreCase("T") ||
                                          cleanedOption.contains("Yes") ||
                                          cleanedOption.equalsIgnoreCase("Yes") ||
                                          cleanedOption.contains("Y") ||
                                          cleanedOption.equalsIgnoreCase("Y");
                    
                    boolean shouldBeFalse = cleanedOption.contains("错误") || 
                                           cleanedOption.equalsIgnoreCase("错误") || 
                                           cleanedOption.contains("错") ||
                                           cleanedOption.equalsIgnoreCase("错") ||
                                           cleanedOption.contains("假") ||
                                           cleanedOption.equalsIgnoreCase("假") ||
                                           cleanedOption.contains("否") ||
                                           cleanedOption.equalsIgnoreCase("否") ||
                                           cleanedOption.contains("×") ||
                                           cleanedOption.contains("✗") ||
                                           cleanedOption.contains("✕") ||
                                           cleanedOption.contains("✖") ||
                                           cleanedOption.contains("❌") ||
                                           cleanedOption.contains("🌑") ||
                                           cleanedOption.contains("✗") ||
                                           cleanedOption.contains("F") ||
                                           cleanedOption.equalsIgnoreCase("F") ||
                                           cleanedOption.contains("No") ||
                                           cleanedOption.equalsIgnoreCase("No") ||
                                           cleanedOption.contains("N") ||
                                           cleanedOption.equalsIgnoreCase("N");
                    
                    // 根据答案内容判断选项是否正确
                    if (answer.equalsIgnoreCase("TRUE")) {
                        return shouldBeTrue;
                    } else if (answer.equalsIgnoreCase("FALSE")) {
                        return shouldBeFalse;
                    }
                    return false;
                } else {
                    // 选择题：将原始索引转换为选项标签（A, B, C...）
                    char optionLabel = (char) ('A' + i);
                    // 检查该选项标签是否包含在答案中
                    return answer.indexOf(optionLabel) != -1;
                }
            }
        }
        
        return false;
    }

    /**
     * 添加问题到题库
     */
    public void addQuestion(Question question) {
        if (question != null) {
            // 输入验证：检查内容长度
            boolean isValid = true;
            
            // 检查题干长度（最大600字符）
            if (question.question != null && question.question.length() > 600) {
                Log.w(TAG, "题目题干过长 (Length: " + question.question.length() + ")，已跳过");
                isValid = false;
            }
            
            // 检查选项长度（转换为JSON字符串后最大250字符）
            if (isValid && question.options != null) {
                String optionsJson = question.options.toString();
                if (optionsJson.length() > 250) {
                    Log.w(TAG, "题目选项过长 (Length: " + optionsJson.length() + ")，已跳过");
                    isValid = false;
                }
            }
            
            // 检查答案长度（最大1000字符）
            if (isValid && question.answer != null && question.answer.length() > 1000) {
                Log.w(TAG, "题目答案过长 (Length: " + question.answer.length() + ")，已跳过");
                isValid = false;
            }
            
            if (isValid) {
                // 使用数据库插入，不需要手动设置ID
                long id = dbHelper.insertQuestion(question);
                if (id != -1) {
                    question.id = (int) id;
                    Log.d(TAG, "成功添加新问题: " + question.question);
                }
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