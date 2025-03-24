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

    //测试数据

    public static void main(String[] args) throws IOException, InterruptedException {
        //获取请求
        //  String url = "https://search.jd.com/Search?keyword=python";
        String url="http://www.tup.tsinghua.edu.cn/booksCenter/booklist.html?keyword=python&keytm=8D383A24958C916A8B";
        // 解析网页 （Jsou返回的Document就是浏览器的Docuement对象）
        Document document = Jsoup.parse(new URL(url), 30000);
//        System.out.println(document);
        //获取id，所有在js里面使用的方法在这里都可以使用
        Element element = document.getElementById("csproduct");
        System.out.println(element);

        //获取所有的li元素
        Elements elements = element.getElementsByTag("li");
   //用来计数
        int c = 0;
        //获取元素中的内容  ，这里的el就是每一个li标签
        for (Element el : elements) {
            c++;
            //这里有一点要注意，直接attr使用src是爬不出来的，因为京东使用了img懒加载
            String img = el.getElementsByTag("img").eq(0).attr("src");
            //获取商品的价格，并且只获取第一个text文本内容
            String isbn = el.getElementsByClass("ft_band_2").first().text();
            String price = el.getElementsByClass("ft_band_2").last().text();
            String title = el.getElementsByTag("span").eq(0).attr("title");
            String authorName = el.getElementsByTag("P").first().text();

            System.out.println("========================================");
            System.out.println(title);
            System.out.println(img);
            System.out.println(authorName);
            System.out.println(isbn);
            System.out.println(price);


        }
        System.out.println(c);
    }

}


