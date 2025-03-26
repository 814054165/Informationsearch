package com.example.demo.service;

import java.lang.*;
import com.alibaba.fastjson.JSON;
import com.example.demo.pojo.Answer;
import com.example.demo.pojo.Content;
import com.example.demo.pojo.Question;
import com.example.demo.utils.JsonParseUtil;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.functionscore.*;
import org.elasticsearch.common.lucene.search.function.CombineFunction;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.AnalyzeRequest;
import org.elasticsearch.client.indices.AnalyzeResponse;
import java.util.stream.Collectors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.example.demo.utils.HtmlParseUtil;

@Service
public class ContentService {

    // 将客户端注入
    @Autowired
    @Qualifier("restHighLevelClient")
    private RestHighLevelClient client;

    public static final String QUESTION_INDEX = "couse_question";
    public static final String ANSWER_INDEX = "couse_answer";
    public static final String GOODS_INDEX = "goods";
    public static final String COLLECTIONS_INDEX = "collections";
    public static final String THU_BOOKS_INDEX = "thu_books";

    // 1、解析数据放到 es 中
    public boolean parseContent(String keyword) throws IOException {
        List<Content> contents = new HtmlParseUtil().parseThu(keyword);
        // 把查询的数据放入 es 中
        BulkRequest request = new BulkRequest();
        request.timeout("2m");

        for (int i = 0; i < contents.size(); i++) {
            request.add(
                    new IndexRequest(GOODS_INDEX)
                            .source(JSON.toJSONString(contents.get(i)), XContentType.JSON));

        }
        BulkResponse bulk = client.bulk(request, RequestOptions.DEFAULT);
        return !bulk.hasFailures();
    }

    // 2、获取这些数据实现基本的搜索功能
    public List<Map<String, Object>> searchPage(String keyword, int pageNo, int pageSize) throws IOException {
        // keyword="机器学习";
        // keyword=keyword.getBytes("UTF-8").toString();
        if (pageNo <= 1) {
            pageNo = 1;
        }
        if (pageSize <= 1) {
            pageSize = 1;
        }

        // 条件搜索
        // SearchRequest searchRequest = new SearchRequest("jd_goods");
        SearchRequest searchRequest = new SearchRequest(THU_BOOKS_INDEX);

        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 分页
        sourceBuilder.from(pageNo).size(pageSize);

        // 精准匹配
        // TermQueryBuilder termQuery = QueryBuilders.termQuery("title", keyword);
        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("title", keyword);

        // sourceBuilder.query(termQuery);
        sourceBuilder.query(matchQuery);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        // 执行搜索
        SearchRequest source = searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        // 解析结果

        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit documentFields : searchResponse.getHits().getHits()) {
            Map<String, Object> sourceMap = documentFields.getSourceAsMap();
            // 添加随机评分 (0-100)
            double score = Math.random() * 100;
            sourceMap.put("score", String.format("%.1f", score));
            list.add(sourceMap);
        }
        return list;
    }

    public boolean addCollection(Content content) throws IOException {
        // 把查询的数据放入 es 中
        BulkRequest request = new BulkRequest();
        request.timeout("2m");

        request.add(
                new IndexRequest(COLLECTIONS_INDEX)
                        .source(JSON.toJSONString(content), XContentType.JSON));

        BulkResponse bulk = client.bulk(request, RequestOptions.DEFAULT);
        return !bulk.hasFailures();
    }

    // 基础搜索函数，处理分页和结果格式化
    private List<Map<String, Object>> executeSearch(SearchRequest searchRequest, SearchSourceBuilder sourceBuilder)
            throws IOException {
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        SearchRequest source = searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit documentFields : searchResponse.getHits().getHits()) {
            Map<String, Object> sourceMap = documentFields.getSourceAsMap();
            float score = documentFields.getScore();
            float normalizedScore = (score / searchResponse.getHits().getMaxScore()) * 100;
            sourceMap.put("score", String.format("%.1f", normalizedScore));

            // 如果是答案搜索，确保返回所需字段
            if (searchRequest.indices()[0].equals(ANSWER_INDEX)) {
                // 如果 atype 字段不存在，使用 aen 作为类型
                if (!sourceMap.containsKey("atype")) {
                    String aen = (String) sourceMap.get("aen");
                    sourceMap.put("atype", aen != null ? aen : "未分类");
                }
                // 确保必要字段存在
                if (!sourceMap.containsKey("aid") || !sourceMap.containsKey("azh")) {
                    continue; // 跳过缺少必要字段的结果
                }
                if (sourceMap.containsKey("azh")) {
                    // 如果 azh 字段存在，使用 azh 的前100个字符作为 azh, 并添加省略号，然后覆盖原来的azh
                    String azh = (String) sourceMap.get("azh");
                    if (azh != null && azh.length() > 100) {
                        sourceMap.put("azh", azh.substring(0, 100) + "...");
                    }
                }
            }

            list.add(sourceMap);
        }

        long total = searchResponse.getHits().getTotalHits().value;
        Map<String, Object> result = new HashMap<>();
        result.put("content", list);
        result.put("total", total);

        return Collections.singletonList(result);
    }

    // 使用BM25算法搜索
    public List<Map<String, Object>> searchQAByBM25(String keyword, int pageNo, int pageSize) throws IOException {
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(QUESTION_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword)
                .operator(Operator.OR);
        sourceBuilder.query(matchQuery);

        return executeSearch(searchRequest, sourceBuilder);
    }

    // 使用Boost算法搜索
    public List<Map<String, Object>> searchQAByBoost(String keyword, int pageNo, int pageSize) throws IOException {
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(QUESTION_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        // String[] keyword_buff = keyword.trim().split(" ");
        // 构造 Analyze 请求，使用 IK 分词器
        AnalyzeRequest request = AnalyzeRequest.withGlobalAnalyzer("ik_smart", keyword);

        // 执行分析
        AnalyzeResponse response = client.indices().analyze(request, RequestOptions.DEFAULT);
        // 提取分词结果
        List<String> tokens = new ArrayList<>();
        for (AnalyzeResponse.AnalyzeToken token : response.getTokens()) {
            tokens.add(token.getTerm());
        }
        System.out.println(tokens);
        if (tokens.size() <= 1) {
            MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
            sourceBuilder.query(matchQuery);
        } else {
            MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("qzh", tokens.get(0));
            matchQuery1.boost(2);

            String keyword_left = tokens.get(1);
            for (int i = 2; i < tokens.size(); i++) {
                keyword_left = " " + tokens.get(i);
            }
            MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("qzh", keyword_left);

            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            boolQueryBuilder.should(matchQuery1);
            boolQueryBuilder.should(matchQuery2);
            sourceBuilder.query(boolQueryBuilder);
        }

        return executeSearch(searchRequest, sourceBuilder);
    }

    // 使用Boosting算法搜索
    public List<Map<String, Object>> searchQAByBoosting(String keyword, int pageNo, int pageSize) throws IOException {
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(QUESTION_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        // 构造 Analyze 请求，使用 IK 分词器
        AnalyzeRequest request = AnalyzeRequest.withGlobalAnalyzer("ik_smart", keyword);

        // 执行分析
        AnalyzeResponse response = client.indices().analyze(request, RequestOptions.DEFAULT);
        // 提取分词结果
        List<String> tokens = new ArrayList<>();
        for (AnalyzeResponse.AnalyzeToken token : response.getTokens()) {
            tokens.add(token.getTerm());
        }

        if (tokens.size() <= 1) {
            MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
            sourceBuilder.query(matchQuery);
        } else {
            MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("qzh", tokens.get(0));
            matchQuery1.boost(2);

            String keyword_left = tokens.get(1);
            for (int i = 2; i < tokens.size(); i++) {
                keyword_left = " " + tokens.get(i);
            }
            MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("qzh", keyword_left);

            BoostingQueryBuilder boosting = QueryBuilders.boostingQuery(matchQuery1, matchQuery2);
            boosting.negativeBoost(0.2f);
            sourceBuilder.query(boosting);
        }

        return executeSearch(searchRequest, sourceBuilder);
    }

    // 使用Script Score算法搜索
    public List<Map<String, Object>> searchQAByScriptScore(String keyword, int pageNo, int pageSize)
            throws IOException {
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(QUESTION_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        // 构造 Analyze 请求，使用 IK 分词器
        AnalyzeRequest request = AnalyzeRequest.withGlobalAnalyzer("ik_smart", keyword);

        // 执行分析
        AnalyzeResponse response = client.indices().analyze(request, RequestOptions.DEFAULT);
        // 提取分词结果
        List<String> tokens = new ArrayList<>();
        for (AnalyzeResponse.AnalyzeToken token : response.getTokens()) {
            tokens.add(token.getTerm());
        }

        if (tokens.size() <= 1) {
            MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
            sourceBuilder.query(matchQuery);
        } else {
            MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("qzh", tokens.get(0));
            matchQuery1.boost(2);

            String keyword_left = tokens.get(1);
            for (int i = 2; i < tokens.size(); i++) {
                keyword_left = " " + tokens.get(i);
            }
            MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("qzh", keyword_left);

            String scoreScript = "int weight=10;\n" +
                    "def random = randomScore(params.uuidHash);\n" +
                    "return weight * random";
            Map<String, Object> paraMap = new HashMap<>();
            int randint = (int) (Math.random() * 100);
            paraMap.put("uuidHash", randint);

            Script script = new Script(Script.DEFAULT_SCRIPT_TYPE, "painless", scoreScript, paraMap);
            ScriptScoreQueryBuilder scriptScoreQueryBuilder = QueryBuilders.scriptScoreQuery(matchQuery2, script);

            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            boolQueryBuilder.should(matchQuery1);
            boolQueryBuilder.should(scriptScoreQueryBuilder);
            sourceBuilder.query(boolQueryBuilder);
        }

        return executeSearch(searchRequest, sourceBuilder);
    }

    // 基于ScriptScore的改进
    public List<Map<String, Object>> searchQAByImproveSC(String keyword, int pageNo, int pageSize) throws IOException {
        // 参数校验
        pageNo = Math.max(pageNo, 1);
        pageSize = Math.max(pageSize, 1);

        SearchRequest searchRequest = new SearchRequest(QUESTION_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .from((pageNo - 1) * pageSize)
                .size(pageSize)
                .timeout(new TimeValue(30, TimeUnit.SECONDS));

        // 1. 分词处理
        List<String> tokens;
        try {
            AnalyzeRequest request = AnalyzeRequest.withGlobalAnalyzer("ik_smart", keyword);
            AnalyzeResponse response = client.indices().analyze(request, RequestOptions.DEFAULT);
            tokens = response.getTokens().stream()
                    .map(AnalyzeResponse.AnalyzeToken::getTerm)
                    .filter(token -> token.length() > 1)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("IK分词失败，使用降级策略: " + e.getMessage());
            sourceBuilder.query(QueryBuilders.matchQuery("qzh", keyword));
            return executeSearch(searchRequest, sourceBuilder);
        }

        // 2. 查询构建
        if (tokens.isEmpty()) {
            // 随机搜索
            sourceBuilder.query(QueryBuilders.functionScoreQuery(
                    QueryBuilders.matchAllQuery(),
                    new RandomScoreFunctionBuilder().seed(System.currentTimeMillis())));
        } else {
            // 动态组合查询
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            // 首词短语匹配
            boolQuery.should(QueryBuilders.matchPhraseQuery("qzh", tokens.get(0)).boost(3f));
            // 其他词组合匹配
            if (tokens.size() > 1) {
                String remaining = String.join(" ", tokens.subList(1, tokens.size()));
                boolQuery.should(QueryBuilders.matchPhraseQuery("qzh", remaining).boost(1.5f));
            }
            // 全词匹配兜底
            boolQuery.should(QueryBuilders.matchQuery("qzh", String.join(" ", tokens)));
            sourceBuilder.query(boolQuery);
        }

        // 3. 执行搜索
        return executeSearch(searchRequest, sourceBuilder);
    }

    // 理想很好但是目前无法实现
    public List<Map<String, Object>> searchQAByFieldValueFactor(String keyword, int pageNo, int pageSize)
            throws IOException {
        // 分页参数校验
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(QUESTION_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 分页设置
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        // 基础查询条件
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 关键词匹配
        String[] keyword_buff = keyword.trim().split(" ");
        if (keyword_buff.length <= 1) {
            boolQuery.must(QueryBuilders.matchQuery("qzh", keyword));
        } else {
            MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("qzh", keyword_buff[0]);
            matchQuery1.boost(2);

            String keyword_left = keyword_buff[1];
            for (int i = 2; i < keyword_buff.length; i++) {
                keyword_left = " " + keyword_buff[i];
            }
            MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("qzh", keyword_left);
            boolQuery.should(matchQuery1).should(matchQuery2);
        }

        // 使用 Field Value Factor 评分
        FieldValueFactorFunctionBuilder fieldValueFactor = ScoreFunctionBuilders
                .fieldValueFactorFunction("popularity") // 假设 popularity 是一个字段，表示受欢迎程度
                .factor(1.2f) // 权重因子
                .modifier(FieldValueFactorFunction.Modifier.LOG1P) // 使用 log(1 + popularity) 计算
                .missing(1); // 如果字段缺失，默认值为 1

        // 将 Field Value Factor 添加到 Function Score Query
        FunctionScoreQueryBuilder functionScoreQuery = QueryBuilders.functionScoreQuery(
                boolQuery, // 基础查询
                fieldValueFactor // 评分函数
        ).boostMode(CombineFunction.MULTIPLY); // 评分计算方式（相乘）

        sourceBuilder.query(functionScoreQuery);

        // 如果不使用 Field Value Factor，直接使用基础查询
        // sourceBuilder.query(boolQuery);

        // 设置超时时间
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

        // 执行搜索
        SearchResponse searchResponse = client.search(searchRequest.source(sourceBuilder), RequestOptions.DEFAULT);

        // 解析结果
        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit documentFields : searchResponse.getHits().getHits()) {
            list.add(documentFields.getSourceAsMap());
        }
        return list;
    }

    // 主搜索函数，默认使用BM25算法
    public List<Map<String, Object>> searchQA(String keyword, int pageNo, int pageSize) throws IOException {
        // searchQAByBM25
        // searchQAByBoost
        // searchQAByBoosting
        // searchQAByScriptScore
        // searchQAByImproveSC
        // searchQAByFieldValueFactore
        return searchQAByBoost(keyword, pageNo, pageSize);
    }

    public List<Map<String, Object>> searchAnswer(String qid) throws IOException {
        // 条件搜索question
        SearchRequest searchRequest = new SearchRequest(QUESTION_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 精准匹配
        TermQueryBuilder termQuery = QueryBuilders.termQuery("qid", qid);
        sourceBuilder.query(termQuery);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        SearchRequest source = searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit documentFields : searchResponse.getHits().getHits()) {
            list.add(documentFields.getSourceAsMap());
        }

        List<Map<String, Object>> list2 = new ArrayList<>();
        String qdomain = "";
        String qzh = "";
        String qen = "";
        String qanswers = "";
        if (!list.isEmpty()) {
            // 条件搜索answer
            searchRequest = new SearchRequest(ANSWER_INDEX);
            qdomain = (String) list.get(0).get("qdomain");
            qzh = (String) list.get(0).get("qzh");
            qen = (String) list.get(0).get("qen");
            qanswers = (String) list.get(0).get("qanswers");
            String[] temp = qanswers.split("\"");

            // 遍历所有aid并搜索对应的答案
            for (int i = 1; i < temp.length; i += 2) {
                String aid = temp[i];
                termQuery = QueryBuilders.termQuery("aid", aid);
                sourceBuilder.query(termQuery);
                sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
                source = searchRequest.source(sourceBuilder);
                searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

                for (SearchHit hit : searchResponse.getHits().getHits()) {
                    Map<String, Object> answerMap = hit.getSourceAsMap();
                    Map<String, Object> filteredMap = new HashMap<>();
                    filteredMap.put("aid", answerMap.get("aid"));
                    filteredMap.put("azh", answerMap.get("azh"));
                    filteredMap.put("aen", answerMap.get("aen"));
                    list2.add(filteredMap);
                }
            }
        }

        List<Map<String, Object>> list3 = new ArrayList<>();
        if (!list2.isEmpty()) {
            Map<String, Object> map1 = new HashMap<String, Object>();
            map1.put("qid", qid);
            map1.put("qdomain", qdomain);
            map1.put("qzh", qzh);
            map1.put("qen", qen);
            map1.put("answerList", list2); // 将答案列表作为一个元素添加
            list3.add(map1);
        }

        return list3;
    }

    public boolean writeQAContent() throws IOException {

        // write quesitons into ES
        String file_path = "D:\\Zy_studyFile\\1_my_learn\\2024-2025\\SecondSemester\\MachinAnswerPractice\\exp1\\question.json";
        List<Question> questionList = new JsonParseUtil().parseJson(file_path);

        // 把查询的数据放入 es 中
        BulkRequest request = new BulkRequest();
        request.timeout("2m");

        for (int i = 0; i < questionList.size(); i++) {
            request.add(
                    new IndexRequest(QUESTION_INDEX)
                            .source(JSON.toJSONString(questionList.get(i)), XContentType.JSON));
        }
        BulkResponse bulk = client.bulk(request, RequestOptions.DEFAULT);

        // write answers into ES
        file_path = "D:\\Zy_studyFile\\1_my_learn\\2024-2025\\SecondSemester\\MachinAnswerPractice\\exp1\\answer.json";
        List<Answer> answerList = new JsonParseUtil().parseAnJson(file_path);

        // 把查询的数据放入 es 中
        request = new BulkRequest();
        request.timeout("2m");

        for (int i = 0; i < answerList.size(); i++) {
            request.add(
                    new IndexRequest(ANSWER_INDEX)
                            .source(JSON.toJSONString(answerList.get(i)), XContentType.JSON));

        }
        bulk = client.bulk(request, RequestOptions.DEFAULT);

        return !bulk.hasFailures();
    }

    // 高级搜索函数
    public List<Map<String, Object>> advancedSearch(String allKeywords, String orKeywords, String notKeywords,
            int pageNo, int pageSize) throws IOException {
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(QUESTION_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 处理必须包含的关键词（AND）
        if (allKeywords != null && !allKeywords.trim().isEmpty()) {
            String[] keywords = allKeywords.trim().split("\\s+");
            for (String keyword : keywords) {
                if (!keyword.isEmpty()) {
                    boolQuery.must(QueryBuilders.matchQuery("qzh", keyword).operator(Operator.AND));
                }
            }
        }

        // 处理至少包含一个的关键词（OR）
        if (orKeywords != null && !orKeywords.trim().isEmpty()) {
            String[] keywords = orKeywords.trim().split("\\s+");
            for (String keyword : keywords) {
                if (!keyword.isEmpty()) {
                    boolQuery.should(QueryBuilders.matchQuery("qzh", keyword).operator(Operator.OR));
                }
            }
        }

        // 处理不包含的关键词（NOT）
        if (notKeywords != null && !notKeywords.trim().isEmpty()) {
            String[] keywords = notKeywords.trim().split("\\s+");
            for (String keyword : keywords) {
                if (!keyword.isEmpty()) {
                    boolQuery.mustNot(QueryBuilders.matchQuery("qzh", keyword));
                }
            }
        }

        // 如果没有添加任何查询条件，添加一个默认的match_all查询
        if (!boolQuery.hasClauses()) {
            boolQuery.must(QueryBuilders.matchAllQuery());
        }

        sourceBuilder.query(boolQuery);
        return executeSearch(searchRequest, sourceBuilder);
    }

    // 答案模式下的高级搜索函数
    public List<Map<String, Object>> advancedAnSearch(String allKeywords, String orKeywords, String notKeywords,
            int pageNo, int pageSize) throws IOException {
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(ANSWER_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 处理必须包含的关键词（AND）
        if (allKeywords != null && !allKeywords.trim().isEmpty()) {
            String[] keywords = allKeywords.trim().split("\\s+");
            for (String keyword : keywords) {
                if (!keyword.isEmpty()) {
                    boolQuery.must(QueryBuilders.matchQuery("azh", keyword).operator(Operator.AND));
                }
            }
        }

        // 处理至少包含一个的关键词（OR）
        if (orKeywords != null && !orKeywords.trim().isEmpty()) {
            String[] keywords = orKeywords.trim().split("\\s+");
            for (String keyword : keywords) {
                if (!keyword.isEmpty()) {
                    boolQuery.should(QueryBuilders.matchQuery("azh", keyword).operator(Operator.OR));
                }
            }
            // 设置最小匹配数为1，确保至少匹配一个关键词
            boolQuery.minimumShouldMatch(1);
        }

        // 处理不包含的关键词（NOT）
        if (notKeywords != null && !notKeywords.trim().isEmpty()) {
            String[] keywords = notKeywords.trim().split("\\s+");
            for (String keyword : keywords) {
                if (!keyword.isEmpty()) {
                    boolQuery.mustNot(QueryBuilders.matchQuery("azh", keyword));
                }
            }
        }

        // 如果没有添加任何查询条件，添加一个默认的match_all查询
        if (!boolQuery.hasClauses()) {
            boolQuery.must(QueryBuilders.matchAllQuery());
        }

        sourceBuilder.query(boolQuery);
        return executeSearch(searchRequest, sourceBuilder);
    }

    // 使用BM25算法搜索答案
    public List<Map<String, Object>> searchAnswerByBM25(String keyword, int pageNo, int pageSize) throws IOException {
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(ANSWER_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("azh", keyword)
                .operator(Operator.OR);
        sourceBuilder.query(matchQuery);

        return executeSearch(searchRequest, sourceBuilder);
    }

    // 使用Boost算法搜索答案
    public List<Map<String, Object>> searchAnswerByBoost(String keyword, int pageNo, int pageSize) throws IOException {
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(ANSWER_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        // 构造 Analyze 请求，使用 IK 分词器
        AnalyzeRequest request = AnalyzeRequest.withGlobalAnalyzer("ik_smart", keyword);

        // 执行分析
        AnalyzeResponse response = client.indices().analyze(request, RequestOptions.DEFAULT);
        // 提取分词结果
        List<String> tokens = new ArrayList<>();
        for (AnalyzeResponse.AnalyzeToken token : response.getTokens()) {
            tokens.add(token.getTerm());
        }

        if (tokens.size() <= 1) {
            MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("azh", keyword);
            sourceBuilder.query(matchQuery);
        } else {
            MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("azh", tokens.get(0));
            matchQuery1.boost(2);

            String keyword_left = tokens.get(1);
            for (int i = 2; i < tokens.size(); i++) {
                keyword_left = " " + tokens.get(i);
            }
            MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("azh", keyword_left);

            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            boolQueryBuilder.should(matchQuery1);
            boolQueryBuilder.should(matchQuery2);
            sourceBuilder.query(boolQueryBuilder);
        }

        return executeSearch(searchRequest, sourceBuilder);
    }

    // 使用Boosting算法搜索答案
    public List<Map<String, Object>> searchAnswerByBoosting(String keyword, int pageNo, int pageSize)
            throws IOException {
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(ANSWER_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        // 构造 Analyze 请求，使用 IK 分词器
        AnalyzeRequest request = AnalyzeRequest.withGlobalAnalyzer("ik_smart", keyword);

        // 执行分析
        AnalyzeResponse response = client.indices().analyze(request, RequestOptions.DEFAULT);
        // 提取分词结果
        List<String> tokens = new ArrayList<>();
        for (AnalyzeResponse.AnalyzeToken token : response.getTokens()) {
            tokens.add(token.getTerm());
        }

        if (tokens.size() <= 1) {
            MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("azh", keyword);
            sourceBuilder.query(matchQuery);
        } else {
            MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("azh", tokens.get(0));
            matchQuery1.boost(2);

            String keyword_left = tokens.get(1);
            for (int i = 2; i < tokens.size(); i++) {
                keyword_left = " " + tokens.get(i);
            }
            MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("azh", keyword_left);

            BoostingQueryBuilder boosting = QueryBuilders.boostingQuery(matchQuery1, matchQuery2);
            boosting.negativeBoost(0.2f);
            sourceBuilder.query(boosting);
        }

        return executeSearch(searchRequest, sourceBuilder);
    }

    // 使用Script Score算法搜索答案
    public List<Map<String, Object>> searchAnswerByScriptScore(String keyword, int pageNo, int pageSize)
            throws IOException {
        if (pageNo <= 1)
            pageNo = 1;
        if (pageSize <= 1)
            pageSize = 1;

        SearchRequest searchRequest = new SearchRequest(ANSWER_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        // 构造 Analyze 请求，使用 IK 分词器
        AnalyzeRequest request = AnalyzeRequest.withGlobalAnalyzer("ik_smart", keyword);

        // 执行分析
        AnalyzeResponse response = client.indices().analyze(request, RequestOptions.DEFAULT);
        // 提取分词结果
        List<String> tokens = new ArrayList<>();
        for (AnalyzeResponse.AnalyzeToken token : response.getTokens()) {
            tokens.add(token.getTerm());
        }

        if (tokens.size() <= 1) {
            MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("azh", keyword);
            sourceBuilder.query(matchQuery);
        } else {
            MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("azh", tokens.get(0));
            matchQuery1.boost(2);

            String keyword_left = tokens.get(1);
            for (int i = 2; i < tokens.size(); i++) {
                keyword_left = " " + tokens.get(i);
            }
            MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("azh", keyword_left);

            String scoreScript = "int weight=10;\n" +
                    "def random = randomScore(params.uuidHash);\n" +
                    "return weight * random";
            Map<String, Object> paraMap = new HashMap<>();
            int randint = (int) (Math.random() * 100);
            paraMap.put("uuidHash", randint);

            Script script = new Script(Script.DEFAULT_SCRIPT_TYPE, "painless", scoreScript, paraMap);
            ScriptScoreQueryBuilder scriptScoreQueryBuilder = QueryBuilders.scriptScoreQuery(matchQuery2, script);

            BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
            boolQueryBuilder.should(matchQuery1);
            boolQueryBuilder.should(scriptScoreQueryBuilder);
            sourceBuilder.query(boolQueryBuilder);
        }

        return executeSearch(searchRequest, sourceBuilder);
    }

    // 基于ScriptScore的改进
    public List<Map<String, Object>> searchAnswerByImproveSC(String keyword, int pageNo, int pageSize)
            throws IOException {
        // 参数校验
        pageNo = Math.max(pageNo, 1);
        pageSize = Math.max(pageSize, 1);

        SearchRequest searchRequest = new SearchRequest(ANSWER_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
                .from((pageNo - 1) * pageSize)
                .size(pageSize)
                .timeout(new TimeValue(30, TimeUnit.SECONDS));

        // 1. 分词处理
        List<String> tokens;
        try {
            AnalyzeRequest request = AnalyzeRequest.withGlobalAnalyzer("ik_smart", keyword);
            AnalyzeResponse response = client.indices().analyze(request, RequestOptions.DEFAULT);
            tokens = response.getTokens().stream()
                    .map(AnalyzeResponse.AnalyzeToken::getTerm)
                    .filter(token -> token.length() > 1)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("IK分词失败，使用降级策略: " + e.getMessage());
            sourceBuilder.query(QueryBuilders.matchQuery("azh", keyword));
            return executeSearch(searchRequest, sourceBuilder);
        }

        // 2. 查询构建
        if (tokens.isEmpty()) {
            // 随机搜索
            sourceBuilder.query(QueryBuilders.functionScoreQuery(
                    QueryBuilders.matchAllQuery(),
                    new RandomScoreFunctionBuilder().seed(System.currentTimeMillis())));
        } else {
            // 动态组合查询
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            // 首词短语匹配
            boolQuery.should(QueryBuilders.matchPhraseQuery("azh", tokens.get(0)).boost(3f));
            // 其他词组合匹配
            if (tokens.size() > 1) {
                String remaining = String.join(" ", tokens.subList(1, tokens.size()));
                boolQuery.should(QueryBuilders.matchPhraseQuery("azh", remaining).boost(1.5f));
            }
            // 全词匹配兜底
            boolQuery.should(QueryBuilders.matchQuery("azh", String.join(" ", tokens)));
            sourceBuilder.query(boolQuery);
        }

        // 3. 执行搜索
        return executeSearch(searchRequest, sourceBuilder);
    }

    // 主答案搜索函数，默认使用BM25算法
    public List<Map<String, Object>> searchByAnswer(String keyword, int pageNo, int pageSize) throws IOException {
        // searchAnswerByBM25
        // searchAnswerByBoost
        // searchAnswerByBoosting
        // searchAnswerByScriptScore
        // searchAnswerByImproveSC
        return searchAnswerByImproveSC(keyword, pageNo, pageSize);
    }

    public List<Map<String, Object>> justAnswer(String aid) throws IOException {
        if (aid == null || aid.trim().isEmpty()) {
            return new ArrayList<>();
        }

        // 首先搜索answer索引获取答案信息
        SearchRequest answerRequest = new SearchRequest(ANSWER_INDEX);
        SearchSourceBuilder answerBuilder = new SearchSourceBuilder();
        TermQueryBuilder termQuery = QueryBuilders.termQuery("aid", aid.trim());
        answerBuilder.query(termQuery);
        answerBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        SearchResponse answerResponse = client.search(answerRequest.source(answerBuilder), RequestOptions.DEFAULT);

        List<Map<String, Object>> result = new ArrayList<>();
        if (answerResponse.getHits().getHits().length > 0) {
            Map<String, Object> answerMap = answerResponse.getHits().getHits()[0].getSourceAsMap();

            // 搜索question索引，通过qanswers字段匹配aid
            SearchRequest questionRequest = new SearchRequest(QUESTION_INDEX);
            SearchSourceBuilder questionBuilder = new SearchSourceBuilder();
            MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qanswers", aid.trim());
            questionBuilder.query(matchQuery);
            questionBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
            SearchResponse questionResponse = client.search(questionRequest.source(questionBuilder),
                    RequestOptions.DEFAULT);

            if (questionResponse.getHits().getHits().length > 0) {
                Map<String, Object> questionMap = questionResponse.getHits().getHits()[0].getSourceAsMap();

                // 构建答案列表
                List<Map<String, Object>> answerList = new ArrayList<>();
                Map<String, Object> filteredAnswerMap = new HashMap<>();
                filteredAnswerMap.put("aid", answerMap.get("aid"));
                filteredAnswerMap.put("azh", answerMap.get("azh"));
                filteredAnswerMap.put("aen", answerMap.get("aen"));
                answerList.add(filteredAnswerMap);

                // 构建包含所有需要字段的结果
                Map<String, Object> resultMap = new HashMap<>();
                resultMap.put("qid", questionMap.get("qid"));
                resultMap.put("qdomain", questionMap.get("qdomain"));
                resultMap.put("qzh", questionMap.get("qzh"));
                resultMap.put("qen", questionMap.get("qen"));
                resultMap.put("answerList", answerList);

                result.add(resultMap);
            }
        }
        return result;
    }

}