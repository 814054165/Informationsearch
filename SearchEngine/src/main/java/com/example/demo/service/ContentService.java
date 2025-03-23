package com.example.demo.service;

import com.alibaba.fastjson.JSON;
import com.example.demo.pojo.Answer;
import com.example.demo.pojo.Content;
import com.example.demo.pojo.Question;
import com.example.demo.utils.HtmlParseUtil;
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
import org.elasticsearch.script.Script;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.ScriptSortBuilder.ScriptSortType;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
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

@Service
public class ContentService {

    @Autowired
    @Qualifier("restHighLevelClient")
    private RestHighLevelClient client;

    public boolean parseContent(String keyword) throws IOException {
        try {
            System.out.println("\n开始爬取清华大学出版社数据，关键词: " + keyword);

            if (client == null) {
                System.out.println("错误: ES客户端为空!");
                return false;
            }

            List<Content> contents = new HtmlParseUtil().parseJD(keyword);

            System.out.println("\n爬取到的数据数量: " + (contents != null ? contents.size() : 0));
            if (contents == null || contents.isEmpty()) {
                System.out.println("没有爬取到任何数据，请检查URL和网页结构");
                return false;
            }

            BulkRequest request = new BulkRequest();
            request.timeout("2m");

            System.out.println("\n准备写入的数据详情：");
            for (Content content : contents) {
                try {
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

    public Map<String, Object> searchPage(String keyword, int pageNo, int pageSize, String type, String sortOrder) throws IOException {
        if (pageNo <= 1) {
            pageNo = 1;
        }
        if (pageSize <= 1) {
            pageSize = 1;
        }

        System.out.println("SearchPage called with: keyword=" + keyword + ", type=" + type + ", sortOrder=" + sortOrder + ", pageNo=" + pageNo);

        SearchRequest searchRequest = new SearchRequest("tsinghua_books");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        sourceBuilder.from((pageNo - 1) * pageSize).size(pageSize);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        if (keyword != null && !keyword.trim().isEmpty()) {
            MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("title", keyword);
            boolQuery.must(matchQuery);
            System.out.println("Added keyword filter: " + keyword);
        }

        if (type != null && !"全部".equals(type)) {
            TermQueryBuilder typeQuery = QueryBuilders.termQuery("type.keyword", type);
            boolQuery.must(typeQuery);
            System.out.println("Added type filter on type.keyword: " + type);
        } else {
            System.out.println("No type filter applied (type is null or '全部')");
        }

        sourceBuilder.query(boolQuery);

        if (sortOrder != null && !sortOrder.isEmpty()) {
            SortOrder order = "asc".equalsIgnoreCase(sortOrder) ? SortOrder.ASC : SortOrder.DESC;
            System.out.println("Applying price sort to entire " + type + " category with order: " + order);
            sourceBuilder.sort(SortBuilders
                    .scriptSort(
                            new Script("doc['price'].size() > 0 && doc['price'].value != null ? Double.parseDouble(doc['price'].value) : 0.0"),
                            ScriptSortType.NUMBER)
                    .order(order));
        } else {
            System.out.println("Applying default score sort to entire " + type + " category");
            sourceBuilder.sort(SortBuilders.scoreSort().order(SortOrder.DESC));
        }

        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));

        try {
            searchRequest.source(sourceBuilder);
            SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            List<Map<String, Object>> list = new ArrayList<>();
            for (SearchHit documentFields : searchResponse.getHits().getHits()) {
                Map<String, Object> sourceMap = documentFields.getSourceAsMap();

                String relativeImagePath = (String) sourceMap.get("img");
                if (relativeImagePath != null && relativeImagePath.startsWith("../")) {
                    relativeImagePath = relativeImagePath.substring(3);
                }
                String completeImagePath = "http://www.tup.tsinghua.edu.cn/" + (relativeImagePath != null ? relativeImagePath : "");
                sourceMap.put("img", completeImagePath);

                list.add(sourceMap);
            }

            long total = searchResponse.getHits().getTotalHits().value;

            Map<String, Object> result = new HashMap<>();
            result.put("results", list);
            result.put("total", total);

            System.out.println("Returning " + list.size() + " results from sorted " + type + " category, total items: " + total);
            return result;
        } catch (Exception e) {
            System.err.println("Error executing search: " + e.getMessage());
            throw new IOException("Search failed", e);
        }
    }

    public List<Map<String, Object>> searchQA(String keyword, int pageNo, int pageSize) throws IOException {
        if (pageNo <= 1) {
            pageNo = 1;
        }
        if (pageSize <= 1) {
            pageSize = 1;
        }

        SearchRequest searchRequest = new SearchRequest("insurance_question");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        sourceBuilder.from(pageNo).size(pageSize);

        MatchQueryBuilder matchQuery = QueryBuilders.matchQuery("qzh", keyword);
        sourceBuilder.query(matchQuery);

        sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
        SearchRequest source = searchRequest.source(sourceBuilder);
        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

        List<Map<String, Object>> list = new ArrayList<>();
        for (SearchHit documentFields : searchResponse.getHits().getHits()) {
            list.add(documentFields.getSourceAsMap());
        }
        return list;
    }

    public List<Map<String, Object>> searchAnswer(String qid) throws IOException {
        SearchRequest searchRequest = new SearchRequest("insurance_question");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

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
        String aid = "";
        if (!list.isEmpty()) {
            searchRequest = new SearchRequest("insurance_answer");
            qdomain = (String) list.get(0).get("qdomain");
            qzh = (String) list.get(0).get("qzh");
            qen = (String) list.get(0).get("qen");
            qanswers = (String) list.get(0).get("qanswers");
            String[] temp = qanswers.split("\"");
            aid = temp[1];
            termQuery = QueryBuilders.termQuery("aid", aid);
            sourceBuilder.query(termQuery);
            sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
            source = searchRequest.source(sourceBuilder);
            searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);

            for (SearchHit documentFields : searchResponse.getHits().getHits()) {
                list2.add(documentFields.getSourceAsMap());
            }
        }

        List<Map<String, Object>> list3 = new ArrayList<>();
        if (!list2.isEmpty()) {
            Map<String, Object> map1 = new HashMap<>();
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
        String file_path = "D:/桌面/课程/信息检索实践/trainnew.json";
        List<Question> questionList = new JsonParseUtil().parseJson(file_path);

        BulkRequest request = new BulkRequest();
        request.timeout("2m");

        for (int i = 0; i < questionList.size(); i++) {
            request.add(
                    new IndexRequest("insurance_question")
                            .source(JSON.toJSONString(questionList.get(i)), XContentType.JSON));
        }
        BulkResponse bulk = client.bulk(request, RequestOptions.DEFAULT);

        file_path = "D:/insuranceqa_data/corpus/pool/answersnew.json";
        List<Answer> answerList = new JsonParseUtil().parseAnJson(file_path);

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

    public void saveSearchHistory(String userId, String keyword) throws IOException {
        System.out.println("Saving search history - userId: " + userId + ", keyword: " + keyword);
        SearchRequest searchRequest = new SearchRequest("search_history");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        TermQueryBuilder termQuery = QueryBuilders.termQuery("userId", userId);
        sourceBuilder.query(termQuery);
        sourceBuilder.size(1);
        searchRequest.source(sourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        List<String> history;

        if (searchResponse.getHits().getTotalHits().value > 0) {
            Map<String, Object> existingDoc = searchResponse.getHits().getHits()[0].getSourceAsMap();
            history = (List<String>) existingDoc.get("history");
            if (!history.contains(keyword)) {
                history.add(0, keyword);
                if (history.size() > 10) history.remove(history.size() - 1);
            }
        } else {
            history = new ArrayList<>();
            history.add(keyword);
        }

        Map<String, Object> doc = new HashMap<>();
        doc.put("userId", userId);
        doc.put("history", history);
        doc.put("timestamp", System.currentTimeMillis());

        IndexRequest indexRequest = new IndexRequest("search_history")
                .id(userId)
                .source(doc, XContentType.JSON);
        client.index(indexRequest, RequestOptions.DEFAULT);
        System.out.println("Search history saved: " + history);
    }

    public List<String> getSearchHistory(String userId) throws IOException {
        System.out.println("Fetching search history for userId: " + userId);
        SearchRequest searchRequest = new SearchRequest("search_history");
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        TermQueryBuilder termQuery = QueryBuilders.termQuery("userId", userId);
        sourceBuilder.query(termQuery);
        sourceBuilder.size(1);
        searchRequest.source(sourceBuilder);

        SearchResponse searchResponse = client.search(searchRequest, RequestOptions.DEFAULT);
        if (searchResponse.getHits().getTotalHits().value > 0) {
            Map<String, Object> doc = searchResponse.getHits().getHits()[0].getSourceAsMap();
            List<String> history = (List<String>) doc.get("history");
            System.out.println("Found search history: " + history);
            return history;
        }
        System.out.println("No search history found for userId: " + userId);
        return new ArrayList<>();
    }
}