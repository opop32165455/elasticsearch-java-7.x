package elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import elasticsearch.annotation.ESField;
import elasticsearch.util.EsAggUtils;
import elasticsearch.util.EsClientUtils;
import elasticsearch.util.EsSearchUtil;
import elasticsearch.util.EsWriteUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Hello world!
 *
 * @author zxc
 */
@Slf4j
public class Demo {
    public static void main(String[] args) throws IOException {
        //client es1:9200
        ElasticsearchClient client = EsClientUtils.getClient();

        String indexName = "test_index";
        String nameField = "name";

        //write
        EsWriteUtils.write(client, indexName, new EsWriteUtils.EsDoc() {
            @ESField
            String name = "a";

            @Override
            public String getDocId() {
                return "a";
            }

            @Override
            public String getRouting() {
                return "a";
            }
        });

        //terms query
        Query query = EsSearchUtil.termsQuery(nameField, "a");

        //search request
        SearchResponse<Object> response = client.search(SearchRequest.of(search -> search
                        .query(query)
                        .index(indexName)
                        .size(5000)
                ),
                //这里使用es 默认的json解析 不太好用 不太灵活
                Object.class);

        //解析response
        List<String> objects = response.hits().hits().stream()
                .map(Hit::source)
                //todo 自己解析json
                .map(String::valueOf)
                .collect(Collectors.toList());

        System.out.println("query data = " + objects);

        //scroll
        EsSearchUtil.scrollHandle(client, query, indexName, new Consumer<List<Object>>() {
            @Override
            public void accept(List<Object> objects) {
                System.out.println("scroll data = " + objects);
            }
        });

        //agg
        HashMap<String, Long> aggResult = EsAggUtils.termsAgg(client, query, nameField + ".keyword", 10, indexName);
        System.out.println("aggResult = " + aggResult);
    }
}
