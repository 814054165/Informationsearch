package com.example.demo.controller;

import com.example.demo.service.ContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class ContentController {

    @Autowired
    private ContentService contentService;

    // @GetMapping("/searchAn/{aid}")
    // public String parsese(Model model, @PathVariable("aid") String aid) throws
    // IOException, IOException {
    //
    // System.out.println(aid);
    // List list=contentService.searchAnswer(aid);
    // String str=list.toString();
    // model.addAttribute("hello",list.toString());
    // return "答案： \n"+ str;
    // }

    @GetMapping("/parse/{keyword}")
    public Boolean parse(@PathVariable("keyword") String keyword) throws IOException, IOException {
        // String convStr="机器学习";
        // convStr=convStr.getBytes("UTF-8").toString();
        System.out.println(keyword);
        // return contentService.parseContent(convStr);
        return contentService.parseContent(keyword);
    }

    @GetMapping("/search/{keyword}/{pageNo}/{pageSize}")
    // @CrossOrigin(origin = {"http://127.0.0.1:8080"})//添加来源地址
    public List<Map<String, Object>> search(@PathVariable("keyword") String keyword,
            @PathVariable("pageNo") int pageNo,
            @PathVariable("pageSize") int pageSize) throws IOException {

        List<Map<String, Object>> list = contentService.searchPage(keyword, pageNo, pageSize);
        return list;
    }

    @GetMapping("/writeQA")
    public Boolean writeQA() throws IOException, IOException {
        return contentService.writeQAContent();
    }

    @PostMapping("/query")
    public List<Map<String, Object>> query(String keyword, int pageNo, int pageSize) throws IOException {

        List<Map<String, Object>> list = contentService.searchPage(keyword, pageNo, pageSize);
        return list;

    }

    @PostMapping("/queryse")
    public List<Map<String, Object>> queryse(String keyword, int pageNo, int pageSize) throws IOException {
        List<Map<String, Object>> list = contentService.searchQA(keyword, pageNo, pageSize);
        return list;
    }

    @PostMapping("/queryse/answer")
    public List<Map<String, Object>> searchByAnswer(@RequestParam("keyword") String keyword,
            @RequestParam(value = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize) throws IOException {
        return contentService.searchByAnswer(keyword, pageNo, pageSize);
    }

    @PostMapping("/advancedSearch")
    public List<Map<String, Object>> advancedSearch(
            @RequestParam(required = false) String allKeywords,
            @RequestParam(required = false) String orKeywords,
            @RequestParam(required = false) String notKeywords,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) throws IOException {
        return contentService.advancedSearch(allKeywords, orKeywords, notKeywords, pageNo, pageSize);
    }

    @PostMapping("/advancedSearch/answer")
    public List<Map<String, Object>> advancedAnSearch(
            @RequestParam(required = false) String allKeywords,
            @RequestParam(required = false) String orKeywords,
            @RequestParam(required = false) String notKeywords,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) throws IOException {
        return contentService.advancedAnSearch(allKeywords, orKeywords, notKeywords, pageNo, pageSize);
    }


}