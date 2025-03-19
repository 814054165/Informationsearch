package com.example.demo;

import com.example.demo.pojo.Content;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Component
public class HtmlParseExample {

    public List<Content> parseTsinghuaBooks(String keyword) throws IOException {
        String url = "http://www.tup.tsinghua.edu.cn/booksCenter/booklist.html?keyword=" + keyword;
        // 解析网页
        Document document = Jsoup.parse(new URL(url), 30000);
        // 获取id
        Element element = document.getElementById("csproduct");
        
        // 获取所有的li元素
        Elements elements = element.getElementsByTag("li");
        
        ArrayList<Content> bookList = new ArrayList<>();
        
        // 获取元素中的内容
        for (Element el : elements) {
            String img = el.getElementsByTag("img").eq(0).attr("src");
            String isbn = el.getElementsByClass("ft_band_2").first().text();
            String price = el.getElementsByClass("ft_band_2").last().text();
            String title = el.getElementsByTag("span").eq(0).attr("title");
            String authorName = el.getElementsByTag("P").first().text();
            
            Content content = new Content();
            content.setTitle(title);
            content.setImg(img);
            content.setPrice(price);
            content.setAuthorName(authorName);
            content.setIsbn(isbn);
            
            bookList.add(content);
        }
        return bookList;
    }

    // 保留原来的main方法用于测试
    public static void main(String[] args) throws IOException, InterruptedException {
        HtmlParseExample parser = new HtmlParseExample();
        List<Content> books = parser.parseTsinghuaBooks("python");
        for (Content book : books) {
            System.out.println("========================================");
            System.out.println(book);
        }
    }
}
