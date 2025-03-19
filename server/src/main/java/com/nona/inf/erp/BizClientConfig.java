package com.nona.inf.erp;

import lombok.Data;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
@ConfigurationProperties(prefix = BizClientConfig.PREFIX)
@Data
public class BizClientConfig {
    public static final String PREFIX = "outer";

    private String baseUrl;


    @Bean
    public PoolingHttpClientConnectionManager connectionManager() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setTimeToLive(TimeValue.ofMinutes(10))
                .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        connectionManager.setMaxTotal(200); // 设置连接池中的最大连接数
        connectionManager.setDefaultMaxPerRoute(50); // 设置每个路由允许的最大连接数
        return connectionManager;
    }

    @Bean
    public BizRpcService erpRpcService(PoolingHttpClientConnectionManager connectionManager) {
        final RestClientAdapter restClientAdapter = RestClientAdapter.create(erpHttpClient(connectionManager));
        return HttpServiceProxyFactory.builderFor(restClientAdapter).build().createClient(BizRpcService.class);
    }

    private RestClient erpHttpClient(PoolingHttpClientConnectionManager connectionManager) {
        final CloseableHttpClient apacheClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setRetryStrategy(new DefaultHttpRequestRetryStrategy(3, TimeValue.ofSeconds(5)))
                .build();


        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("appid", "my id")
                .defaultHeader("secret", "fake secret")
                .requestFactory(new HttpComponentsClientHttpRequestFactory(apacheClient))
                .build();
    }


}