package com.example.demo.controller;

import com.example.demo.service.ContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ContentController {

    @Autowired
    private ContentService contentService;

    @GetMapping("/parse/{keyword}")
    public Boolean parse(@PathVariable("keyword") String keyword) throws IOException {
        System.out.println("Parsing content for keyword: " + keyword);
        return contentService.parseContent(keyword);
    }

    @GetMapping("/search/{keyword}/{pageNo}/{pageSize}/{type}/{sortOrder}")
    public ResponseEntity<Map<String, Object>> search(@PathVariable("keyword") String keyword,
                                                      @PathVariable("pageNo") int pageNo,
                                                      @PathVariable("pageSize") int pageSize,
                                                      @PathVariable("type") String type,
                                                      @PathVariable("sortOrder") String sortOrder) throws IOException {
        System.out.println("GET search called with: keyword=" + keyword + ", type=" + type + ", sortOrder=" + sortOrder + ", pageNo=" + pageNo + ", pageSize=" + pageSize);
        Map<String, Object> result = contentService.searchPage(keyword, pageNo, pageSize, type, sortOrder);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/writeQA")
    public Boolean writeQA() throws IOException {
        System.out.println("Writing QA content");
        return contentService.writeQAContent();
    }

    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> query(@RequestParam String keyword,
                                                     @RequestParam int pageNo,
                                                     @RequestParam int pageSize,
                                                     @RequestParam String type,
                                                     @RequestParam(defaultValue = "") String sortOrder) throws IOException {
        System.out.println("POST query called with: keyword=" + keyword + ", type=" + type + ", sortOrder=" + sortOrder + ", pageNo=" + pageNo);
        Map<String, Object> result = contentService.searchPage(keyword, pageNo, pageSize, type, sortOrder);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/queryse")
    public List<Map<String, Object>> queryse(@RequestParam String keyword,
                                             @RequestParam int pageNo,
                                             @RequestParam int pageSize) throws IOException {
        System.out.println("POST queryse called with: keyword=" + keyword);
        return contentService.searchQA(keyword, pageNo, pageSize);
    }

    @PostMapping("/saveSearchHistory")
    public ResponseEntity<Void> saveSearchHistory(@RequestParam String userId, @RequestParam String keyword) throws IOException {
        System.out.println("Saving search history for user: " + userId + ", keyword: " + keyword);
        contentService.saveSearchHistory(userId, keyword);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/getSearchHistory")
    public ResponseEntity<Map<String, List<String>>> getSearchHistory(@RequestParam String userId) throws IOException {
        System.out.println("Getting search history for user: " + userId);
        List<String> history = contentService.getSearchHistory(userId);
        Map<String, List<String>> response = new HashMap<>();
        response.put("history", history);
        System.out.println("Returning search history: " + history);
        return ResponseEntity.ok(response);
    }
}