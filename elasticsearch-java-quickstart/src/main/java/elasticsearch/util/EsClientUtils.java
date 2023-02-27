package elasticsearch.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.dialect.Props;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhangxuecheng4441
 * @date 2022/4/7/007 10:48
 */
@Slf4j
public class EsClientUtils {
    /**
     * restClient
     */
    private static final RestClient REST_CLIENT = RestClient.builder(getDefaultEsHosts().toArray(new HttpHost[]{})).build();

    /**
     * Create the transport with a Jackson mapper
     */
    private static final ElasticsearchTransport TRANSPORT = new RestClientTransport(
            REST_CLIENT, new JacksonJsonpMapper());

    /**
     * And create the API client
     */
    private static final ElasticsearchClient CLIENT = new ElasticsearchClient(TRANSPORT);

    public static ElasticsearchClient getClient() {
        return CLIENT;
    }


    /**
     * 判断索引是否存在
     *
     * @param index index name
     * @return is exists
     */
    public static Boolean indexIsExist(String index) {
        try {
            BooleanResponse exists = EsClientUtils.getClient().indices().exists(iq -> iq.index(index));
            return exists.value();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    /**
     * 创建索引
     *
     * @param index index name
     */
    public static void createIndex(String index) {
        try {
            EsClientUtils.getClient().indices().create(cir -> cir.index(index));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

    }

    /**
     * http 地址
     *
     * @param hosts hosts
     * @return List<HttpHost>
     */
    public static List<HttpHost> getEsHosts(String hosts) {
        List<HttpHost> httpHosts = new ArrayList<>();
        String[] split = hosts.split(StrUtil.COMMA);
        for (String s : split) {
            httpHosts.add(HttpHost.create(s));
        }
        return httpHosts;
    }


    public static List<HttpHost> getDefaultEsHosts() {
        String esHostsConfKey = "es.hosts";
        return getEsHosts(new Props("application.properties", Charset.defaultCharset()).getStr(esHostsConfKey, "es1:9200"));
    }
}
