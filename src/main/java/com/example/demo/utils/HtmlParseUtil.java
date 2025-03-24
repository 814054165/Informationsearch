package com.example.demo.utils;

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


public class HtmlParseUtil {
    public static void main(String[] args) throws IOException {
        new HtmlParseUtil().parseThu("python").forEach(System.out::println);
    }

    public List<Content> parseJD(String keywords) throws IOException {
        //获取请求 https://search.jd.com/Search?keyword=java&enc=utf-8
        //前提需要连网
        //keywords="人工智能";
        String url = "https://search.jd.com/Search?keyword=" + keywords + "&enc=utf-8";
        System.out.println(url);

        //解析网页 (Jsoup返回Document就是浏览器Document对象)
        Document document = Jsoup.parse(new URL(url), 30000);


        System.out.println("----------------");
        //所有在js中能使用的方法,这里都能用
        Element element = document.getElementById("J_goodsList");
        //获取所有li元素
        Elements elements = element.getElementsByTag("li");

        ArrayList<Content> goodList = new ArrayList<>();

        //通过元素中的内容,这里el就是每一个li标签了
        for (Element el : elements) {
            //加if判断是为了 过滤空标签
            if (el.attr("class").equalsIgnoreCase("gl-item")) {
                //关于这种图片特别多的网页,所有的图片都是延迟加载的
                //在jd搜索后f12可以看到存放在data-lazy-img中
                String img = el.getElementsByTag("img").eq(0).attr("data-lazy-img");
                String price = el.getElementsByClass("p-price").eq(0).text();
                String title = el.getElementsByClass("p-name").eq(0).text();

                System.out.println(img+price+title);
                Content content = new Content();
                content.setImg(img);
                content.setPrice(price);
                content.setTitle(title);
                goodList.add(content);
            }

        }
        return goodList;
    }
    public List<Content> parseThu(String keywords) throws IOException {
        // 初始页码
        int page = 1;
        // 用于存储所有书籍的列表
        List<Content> allBooks = new ArrayList<>();

        // 根据页码循环爬取
        for (page = 1; page <= 5; ) {
            // 构造分页的URL，这里假设分页参数为page
            String url = "http://www.tup.tsinghua.edu.cn/booksCenter/booklist.html?" +
                    "keyword=" + keywords +
                    "&keytm=8D383A22968496608A" +
                    "&page=" + page;

            System.out.println("正在爬取: " + url);

            // 解析网页
            Document document = Jsoup.parse(new URL(url), 30000);

            // 获取所有书籍的元素
            Element element = document.getElementById("csproduct");
            Elements elements = element.getElementsByTag("li");

            // 如果当前页面没有书籍，说明已爬取完所有页面，跳出循环
            if (elements.isEmpty()) {
                break;
            }

            // 遍历当前页面的所有书籍元素
            for (Element el : elements) {
                if (!el.getElementsByTag("a").isEmpty()) {
                    // 提取图片链接
                    String img = el.getElementsByTag("img").eq(0).attr("src");
                    String root = "http://www.tup.tsinghua.edu.cn/booksCenter/";
                    img = root + img;

                    // 提取书籍标题
                    String title = el.getElementsByTag("span").eq(0).attr("title");
                    // 提取作者信息
                    String authorName = el.getElementsByTag("p").first().text();
                    // 提取ISBN（ft_band_2类的内容）
                    String isbn = el.getElementsByClass("ft_band_2").first().text();
                    // 提取价格（ft_band_2类的最后一个内容）
                    String price = el.getElementsByClass("ft_band_2").last().text();

                    // 将书籍信息存入Content对象
                    Content content = new Content();
                    content.setImg(img);
                    content.setPrice(price);
                    content.setTitle(title);
                    // content.setAuthorName(authorName);
                    // content.setIsbn(isbn);

                    // 添加到所有书籍的列表中
                    allBooks.add(content);
                }
            }

            // 增加页码，继续爬取下一页
            page++;
        }

        return allBooks;
    }

}
//
//public class HtmlParseUtil {
//
//    //测试数据
//    public static void main(String[] args) throws IOException, InterruptedException {
//        //获取请求
//        String url = "https://search.jd.com/Search?keyword=python";
//        // 解析网页 （Jsou返回的Document就是浏览器的Docuement对象）
//        Document document = Jsoup.parse(new URL(url), 30000);
//        //获取id，所有在js里面使用的方法在这里都可以使用
//        Element element = document.getElementById("J_goodsList");
//        //获取所有的li元素
//        Elements elements = element.getElementsByTag("li");
//        //用来计数
//        int c = 0;
//        //获取元素中的内容  ，这里的el就是每一个li标签
//        for (Element el : elements) {
//            c++;
//            //这里有一点要注意，直接attr使用src是爬不出来的，因为京东使用了img懒加载
//            String img = el.getElementsByTag("img").eq(0).attr("data-lazy-img");
//            //获取商品的价格，并且只获取第一个text文本内容
//            String price = el.getElementsByClass("p-price").eq(0).text();
//            String title = el.getElementsByClass("p-name").eq(0).text();
//            String shopName = el.getElementsByClass("p-shop").eq(0).text();
//
//            System.out.println("========================================");
//            System.out.println(img);
//            System.out.println(price);
//            System.out.println(title);
//            System.out.println(shopName);
//        }
//        System.out.println(c);
//    }
//}
