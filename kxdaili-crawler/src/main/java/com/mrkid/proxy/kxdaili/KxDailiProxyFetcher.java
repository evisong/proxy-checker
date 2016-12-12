package com.mrkid.proxy.kxdaili;

import com.mrkid.proxy.Crawl4jProxyFetcher;
import com.mrkid.proxy.dto.ProxyDTO;
import com.mrkid.proxy.dto.Source;
import edu.uci.ics.crawler4j.crawler.CrawlController;
import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.parser.HtmlParseData;
import edu.uci.ics.crawler4j.url.WebURL;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * User: xudong
 * Date: 03/11/2016
 * Time: 12:47 PM
 */

public class KxDailiProxyFetcher extends Crawl4jProxyFetcher {

    public static final String STORE_ROOT = "./crawl/kxdaili/root";
    public static final String SEED = "http://www.kxdaili.com/dailiip.html";

    @Override
    protected String getStoreRoot() {
        return STORE_ROOT;
    }

    @Override
    protected List<String> getSeeds() {
        return Arrays.asList(SEED);
    }

    @Override
    protected int getPolitenessDelay() {
        return 0;
    }

    @Override
    protected CrawlController.WebCrawlerFactory<WebCrawler> getWebCrawlerFactory() {
        return () -> new KxDailiCrawler();
    }
}

class KxDailiCrawler extends WebCrawler {

    private List<ProxyDTO> result = new ArrayList<>();

    @Override
    public boolean shouldVisit(Page referringPage, WebURL url) {
        return url.getURL().startsWith("http://www.kxdaili.com/dailiip");
    }

    /**
     * This function is called when a page is fetched and ready
     * to be processed by your program.
     */
    @Override
    public void visit(Page page) {
        String url = page.getWebURL().getURL();
        System.out.println("URL: " + url);

        if (page.getParseData() instanceof HtmlParseData) {
            HtmlParseData htmlParseData = (HtmlParseData) page.getParseData();
            String html = htmlParseData.getHtml();

            final Document doc = Jsoup.parse(html);
            final Elements tables = doc.select("div table");

            final List<ProxyDTO> proxies = tables.stream().map(table -> extractProxies(table)).flatMap(l -> l.stream())
                    .collect(Collectors.toList());

            proxies.forEach(p->result.add(p));
        }
    }

    private List<ProxyDTO> extractProxies(Element table) {
        final Elements header = table.select("thead th");
        final Elements rows = table.select("tbody tr");

        return rows.stream().map(row -> {
            final int size = header.size();

            String host = "";
            int port = 0;

            String location = null;
            Date lastCheckSuccess = null;

            Elements cells = row.select("td");

            for (int i = 0; i < size; i++) {
                String headerName = header.get(i).text();
                switch (headerName) {
                    case "IP地址":
                        host = cells.get(i).text();
                        break;
                    case "端口":
                        port = Integer.valueOf(cells.get(i).text());
                        break;
                    case "地理位置":
                        location = cells.get(i).text();
                        break;
                    default:
                }

            }

            if (StringUtils.isBlank(host) || port == 0) {
                return null;
            }

            ProxyDTO proxy = new ProxyDTO("http", host, port);
            proxy.setLocation(location);

            proxy.setSource(Source.KXDAILI.name());


            return proxy;
        }).filter(p -> p != null).collect(Collectors.toList());
    }

    @Override
    public List<ProxyDTO> getMyLocalData() {
        return result;
    }
}