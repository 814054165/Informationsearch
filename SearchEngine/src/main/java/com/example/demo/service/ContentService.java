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
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.get.GetIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.*;
import org.elasticsearch.index.query.functionscore.ScriptScoreQueryBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.example.demo.utils.HtmlParseUtil;
import com.example.demo.HtmlParseExample;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URL;

@Service
public class ContentService {

    // 将客户端注入
    @Autowired
    @Qualifier("restHighLevelClient")
    private RestHighLevelClient client;

    // 1、解析数据放到 es 中
    public boolean parseContent(String keyword) throws IOException {
        try {
            System.out.println("\n开始爬取清华大学出版社数据，关键词: " + keyword);

            // 检查ES客户端
            if (client == null) {
                System.out.println("错误: ES客户端为空!");
                return false;
            }

            // 爬取数据
            List<Content> contents = new HtmlParseUtil().parseJD(keyword);

            // 添加调试信息
            System.out.println("\n爬取到的数据数量: " + (contents != null ? contents.size() : 0));
            if (contents == null || contents.isEmpty()) {
                System.out.println("没有爬取到任何数据，请检查URL和网页结构");
                return false;
            }

            // 把查询的数据放入 es 中
            BulkRequest request = new BulkRequest();
            request.timeout("2m");

            System.out.println("\n准备写入的数据详情：");
            for (Content content : contents) {
                try {
                    // 打印每条数据的详细信息
                    System.out.println("\n-------------------");
                    System.out.println("标题: " + content.getTitle());
                    System.out.println("作者: " + content.getAuthorName());
                    System.out.println("ISBN: " + content.getIsbn());
                    System.out.println("价格: " + content.getPrice());
                    System.out.println("图片URL: " + content.getImg());

                    String jsonString = JSON.toJSONString(content);
                    System.out.println("JSON数据: " + jsonString);

                    IndexRequest indexRequest = new IndexRequest("tsinghua_books");
                    indexRequest.source(jsonString, XContentType.JSON);
                    request.add(indexRequest);
                } catch (Exception e) {
                    System.out.println("处理数据时出错: " + e.getMessage());
                }
            }

            if (request.numberOfActions() == 0) {
                System.out.println("\n错误: 没有数据被添加到请求中");
                return false;
            }

            System.out.println("\n开始批量写入ES，总计 " + request.numberOfActions() + " 条数据...");
            BulkResponse bulk = client.bulk(request, RequestOptions.DEFAULT);

            if (bulk.hasFailures()) {
                System.out.println("\nES写入失败: " + bulk.buildFailureMessage());
                return false;
            }

            System.out.println("\nES写入成功！");
            System.out.println("总数据条数: " + contents.size());
            System.out.println("写入耗时: " + bulk.getTook().getStringRep());
            System.out.println("是否有失败: " + bulk.hasFailures());

            return true;

        } catch (Exception e) {
            System.out.println("\n发生异常: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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
        SearchRequest searchRequest = new SearchRequest("tsinghua_books");

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

            // 获取相对路径
            String relativeImagePath = (String) sourceMap.get("img");
            if (relativeImagePath.startsWith("../")) {
                relativeImagePath = relativeImagePath.substring(3); // 去掉多余的 "../"
            }
            // 拼接完整的外部 URL
            String completeImagePath = "http://www.tup.tsinghua.edu.cn/" + relativeImagePath;

            // 将完整的 URL 放回 Map 中
            sourceMap.put("img", completeImagePath);

            // 添加到结果列表
            list.add(sourceMap);
        }
        return list;
    }

    public List<Map<String, Object>> searchQA(String keyword, int pageNo, int pageSize) throws IOException {
        // keyword="机器学习";
        // keyword=keyword.getBytes("UTF-8").toString();
        if (pageNo <= 1) {
            pageNo = 1;
        }
        if (pageSize <= 1) {
            pageSize = 1;
        }

        // 条件搜索
        SearchRequest searchRequest = new SearchRequest("insurance_question");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 分页
        sourceBuilder.from(pageNo).size(pageSize);

        // 精准匹配 --- 不调整排序算法
        // TermQueryBuilder termQuery = QueryBuilders.termQuery("title", keyword);
        // sourceBuilder.query(termQuery);

        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
        sourceBuilder.query(matchQuery);

        // 调整排序算法 ---boost
        // String[] keyword_buff = keyword.trim().split(" ");
        // if(keyword_buff.length<=1){
        // MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
        // sourceBuilder.query(matchQuery);
        // }
        // else{
        // MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("qzh",
        // keyword_buff[0]);
        // matchQuery1.boost(2);
        //
        // String keyword_left=keyword_buff[1];
        // for(int i=2;i<keyword_buff.length;i++){
        // keyword_left=" "+keyword_buff[i];
        // }
        // MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("qzh",
        // keyword_left);
        // BoolQueryBuilder boolQueryBuilder=QueryBuilders.boolQuery();
        // boolQueryBuilder.should(matchQuery1);
        // boolQueryBuilder.should(matchQuery2);
        // sourceBuilder.query(boolQueryBuilder);
        // }

        // 调整排序算法 ---boost positive and negative
        // String[] keyword_buff = keyword.trim().split(" ");
        // if(keyword_buff.length<=1){
        // MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
        // sourceBuilder.query(matchQuery);
        // }
        // else{
        // MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("qzh",
        // keyword_buff[0]);
        // matchQuery1.boost(2);
        //
        // String keyword_left=keyword_buff[1];
        // for(int i=2;i<keyword_buff.length;i++){
        // keyword_left=" "+keyword_buff[i];
        // }
        // MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("qzh",
        // keyword_left);
        // BoostingQueryBuilder
        // boosting=QueryBuilders.boostingQuery(matchQuery1,matchQuery2);
        // boosting.negativeBoost(0.2f);
        // sourceBuilder.query(boosting);
        // }

        // 调整排序算法 ---使用script score
        // String[] keyword_buff = keyword.trim().split(" ");
        // if(keyword_buff.length<=1){
        // MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
        // sourceBuilder.query(matchQuery);
        // }
        // else{
        // MatchQueryBuilder matchQuery1 = QueryBuilders.matchQuery("qzh",
        // keyword_buff[0]);
        // matchQuery1.boost(2);
        //
        // String keyword_left=keyword_buff[1];
        // for(int i=2;i<keyword_buff.length;i++){
        // keyword_left=" "+keyword_buff[i];
        // }
        // MatchQueryBuilder matchQuery2 = QueryBuilders.matchQuery("qzh",
        // keyword_left);
        // String scoreScript ="int weight=10;\n"+
        // "def random= randomScore(params.uuidHash);\n"+
        // "return weight*random";
        // Map paraMap=new HashMap();
        // int randint=(int)(Math.random()*100);
        // System.out.println(randint);
        // paraMap.put("uuidHash",randint);
        // Script script=new
        // Script(Script.DEFAULT_SCRIPT_TYPE,"painless",scoreScript,paraMap);
        // ScriptScoreQueryBuilder
        // scriptScoreQueryBuilder=QueryBuilders.scriptScoreQuery(matchQuery2,script);
        // BoolQueryBuilder boolQueryBuilder=QueryBuilders.boolQuery();
        // boolQueryBuilder.should(matchQuery1);
        // boolQueryBuilder.should(scriptScoreQueryBuilder);
        // sourceBuilder.query(boolQueryBuilder);
        // }

        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        // 执行搜索
        SearchRequest source = searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        // 解析结果

        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit documentFields : searchResponse.getHits().getHits()) {
            list.add(documentFields.getSourceAsMap());
        }
        return list;
    }

    public List<Map<String, Object>> searchAnswer(String qid) throws IOException {
        // 条件搜索insurance_question
        SearchRequest searchRequest = new SearchRequest("insurance_question");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        // 精准匹配
        TermQueryBuilder termQuery = QueryBuilders.termQuery("qid", qid);
        // TermQueryBuilder matchQuery = QueryBuilders.termQuery("qid", qid);

        sourceBuilder.query(termQuery);
        // sourceBuilder.query(matchQuery);
        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        // 执行搜索
        SearchRequest source = searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        // 解析结果

        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit documentFields : searchResponse.getHits().getHits()) {
            list.add(documentFields.getSourceAsMap());
        }

        //
        List<Map<String, Object>> list2 = new ArrayList<>();
        String qdomain = "";
        String qzh = "";
        String qen = "";
        String qanswers = "";
        String aid = "";
        if (!list.isEmpty()) {
            // 条件搜索insurance_answer
            searchRequest = new SearchRequest("insurance_answer");
            qdomain = (String) list.get(0).get("qdomain");
            qzh = (String) list.get(0).get("qzh");
            qen = (String) list.get(0).get("qen");
            qanswers = (String) list.get(0).get("qanswers");
            String[] temp;
            temp = qanswers.split("\"");
            aid = temp[1];
            // 精准匹配
            termQuery = QueryBuilders.termQuery("aid", aid);
            // TermQueryBuilder matchQuery = QueryBuilders.termQuery("qid", qid);

            sourceBuilder.query(termQuery);
            // sourceBuilder.query(matchQuery);
            sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
            // 执行搜索
            source = searchRequest.source(sourceBuilder);
            searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
            // 解析结果

            for (SearchHit documentFields : searchResponse.getHits().getHits()) {
                list2.add(documentFields.getSourceAsMap());
            }
        }
        ;
        List<Map<String, Object>> list3 = new ArrayList<>();

        if (!list2.isEmpty()) {
            Map<String, Object> map1 = new HashMap<String, Object>();
            map1.put("qid", qid);
            map1.put("qdomain", qdomain);
            map1.put("qzh", qzh);
            map1.put("qen", qen);
            map1.put("aid", (String) list2.get(0).get("aid"));
            map1.put("azh", (String) list2.get(0).get("azh"));
            map1.put("aen", (String) list2.get(0).get("aen"));
            list3.add(map1);
        }

        return list3;
    }

    public boolean writeQAContent() throws IOException {

        // write quesitons into ES
        String file_path = "D:/桌面/课程/信息检索实践/trainnew.json";
        List<Question> questionList = new JsonParseUtil().parseJson(file_path);

        // 把查询的数据放入 es 中
        BulkRequest request = new BulkRequest();
        request.timeout("2m");

        for (int i = 0; i < questionList.size(); i++) {
            request.add(
                    new IndexRequest("insurance_question")
                            .source(JSON.toJSONString(questionList.get(i)), XContentType.JSON));
        }
        BulkResponse bulk = client.bulk(request, RequestOptions.DEFAULT);

        // write answers into ES
        file_path = "D:/insuranceqa_data/corpus/pool/answersnew.json";
        List<Answer> answerList = new JsonParseUtil().parseAnJson(file_path);

        // 把查询的数据放入 es 中
        request = new BulkRequest();
        request.timeout("2m");

        for (int i = 0; i < answerList.size(); i++) {
            request.add(
                    new IndexRequest("insurance_answer")
                            .source(JSON.toJSONString(answerList.get(i)), XContentType.JSON));

        }
        bulk = client.bulk(request, RequestOptions.DEFAULT);

        return !bulk.hasFailures();
    }

}
