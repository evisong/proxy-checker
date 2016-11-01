package com.mrkid.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrkid.proxy.Proxy;
import com.mrkid.proxy.ProxyCheckResponse;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.protocol.HttpContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * User: xudong
 * Date: 31/10/2016
 * Time: 3:39 PM
 */
@Component
public class Scheduler {
    private ObjectMapper objectMapper = new ObjectMapper();

    private final int concurrentPermits = 100;
    @Autowired
    private CloseableHttpAsyncClient httpclient;

    public List<ProxyCheckResponse> check(String originIp, String proxyCheckerUrl, List<Proxy>
            proxies) throws IOException {

        Semaphore semaphore = new Semaphore(concurrentPermits);
        final List<CompletableFuture<ProxyCheckResponse>> futures = proxies.stream().map(proxy -> {

                    try {
                        semaphore.acquire();
                        return getProxyResponse(originIp, proxyCheckerUrl, proxy)
                                .whenComplete((t, u) -> semaphore.release());
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        final CompletableFuture<ProxyCheckResponse> future = new CompletableFuture<>();
                        future.complete(new ProxyCheckResponse("", "", "", proxy, false));
                        return future;
                    }
                }

        ).collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
                .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList())).join();


    }

    private CompletableFuture<ProxyCheckResponse> getProxyResponse(String originIp,
                                                                   String proxyCheckerUrl, Proxy proxy) {

        CompletableFuture<ProxyCheckResponse> promise = new CompletableFuture<>();

        final ProxyCheckResponse errorResponse = new ProxyCheckResponse("", "", "", proxy, false);

        HttpPost request = new HttpPost(proxyCheckerUrl + "?originIp=" + originIp);


        HttpContext httpContext = HttpClientContext.create();


        if (proxy.getSchema().equalsIgnoreCase("socks5") || proxy.getSchema().equalsIgnoreCase("socks4")) {
            httpContext.setAttribute("socks.address", new InetSocketAddress(proxy.getHost(), proxy.getPort()));
        } else if (proxy.getSchema().equalsIgnoreCase("http") || proxy.getSchema().equalsIgnoreCase("https")) {
            RequestConfig config = RequestConfig.custom()
                    .setProxy(new HttpHost(proxy.getHost(), proxy.getPort(), proxy.getSchema().toLowerCase()))
                    .build();

            request.setConfig(config);
        }


        try {
            request.setEntity(new StringEntity(objectMapper.writeValueAsString(proxy), ContentType.APPLICATION_JSON));
        } catch (JsonProcessingException e) {
        }

        httpclient.execute(request, httpContext, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse httpResponse) {
                if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    promise.complete(errorResponse);
                } else {
                    try {
                        final String value = IOUtils.toString(httpResponse.getEntity().getContent(), "utf-8");
                        promise.complete(objectMapper.readValue(value, ProxyCheckResponse.class));
                    } catch (IOException e) {
                        e.printStackTrace();
                        promise.complete(errorResponse);
                    }
                }

            }

            @Override
            public void failed(Exception e) {
                e.printStackTrace();
                promise.complete(errorResponse);
            }

            @Override
            public void cancelled() {
                promise.cancel(false);
            }
        });

        return promise;
    }
}
