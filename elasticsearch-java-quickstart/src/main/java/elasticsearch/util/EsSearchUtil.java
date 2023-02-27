package elasticsearch.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.CharUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static cn.hutool.core.text.StrPool.COMMA;

/**
 * @author zxc
 * @date 2022/6/22/022 11:08
 */
@Slf4j
public class EsSearchUtil {

    /**
     * index 切割
     *
     * @param indexStr aaa,bbb
     * @return [aaa, bbb]
     */
    public static String[] getSearchIndex(String indexStr) {
        return StrUtil.splitToArray(indexStr, CharUtil.COMMA);
    }

    /**
     * 构造一个简单terms函数
     *
     * @param field  字段
     * @param values values
     * @return Query
     */
    public static Query termsQuery(String field, Collection<String> values) {
        int batch = 1000;
        ArrayList<Query> list = new ArrayList<>();

        //1000个数据切割一次
        for (List<String> value1000 : CollUtil.split(values, batch)) {
            Query query = Query.of(qb -> qb
                    .terms(termsBuilder -> termsBuilder
                            .field(field)
                            .terms(tf -> tf.value(EsCommonUtils.strList2Field(value1000)))
                    )
            );
            list.add(query);
        }

        //拼成一个查询语句
        return Query.of(qb -> qb
                .bool(bool -> bool
                        .should(list)
                )
        );
    }

    /**
     * 构造一个简单terms函数
     *
     * @param field  字段
     * @param values values
     * @return Query
     */
    public static Query termsQuery(String field, String... values) {
        return Query.of(qb -> qb
                .terms(termsBuilder -> termsBuilder
                        .field(field)
                        .terms(tf -> tf.value(EsCommonUtils.str2Field(values)))
                )
        );
    }

    /**
     * 构造一个简单match函数
     *
     * @param field 字段
     * @param value value es7支持match:"aaa,bbb,ccc"
     * @return Query
     */
    public static Query matchQuery(String field, String value) {
        return Query.of(qb -> qb
                .match(match -> match
                        .field(field)
                        .query(value)
                )
        );
    }

    /**
     * match all query
     *
     * @return match all query
     */
    public static Query matchAll() {
        return Query.of(qb -> qb
                .matchAll(match -> match
                )
        );
    }

    /**
     * exists query
     *
     * @param field field
     * @return query
     */
    public static Query existsQuery(String field) {
        return Query.of(q -> q.exists(
                e -> e.field(field)
        ));
    }

    /**
     * es dsl查询语句 生成query
     *
     * @param dsl es dsl
     * @return query
     */
    public static Query dslParseQuery(String dsl) {
        return Query.of(q -> q.withJson(new StringReader(dsl)));
    }

    /**
     * es dsl查询语句 生成query
     *
     * @param query query
     * @return dsl str
     */
    public static String query2Dsl(Query query) {
        return StrUtil.removePrefix(query.toString(), "Query:");
    }

    /**
     * scroll查询出数据，然后根据需要处理
     *
     * @param client       ElasticsearchClient
     * @param query        Query.of()
     * @param index        index
     * @param dataConsumer 数据消费处理函数
     * @throws IOException IOException
     */
    public static void scrollHandle(ElasticsearchClient client, Query query, List<String> index, Consumer<List<Object>> dataConsumer) throws IOException {
        long total = 0;

        SearchResponse<Object> response = client.search(
                SearchRequest.of(sear -> sear
                        .query(query)
                        .scroll(Time.of(ts -> ts.time("2m")))
                        .index(index)
                        .size(5000)
                )
                //此处使用es默认的反序列化 容易出现问题
                , Object.class
        );
        log.info("search from index:{},dsl:\r\n{}", index, query.toString());

        if (CollUtil.isEmpty(response.hits().hits())) {
            log.warn("can not find result from this search");
            return;
        }

        List<Object> objects = response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
        dataConsumer.accept(objects);

        log.info("search size:{}", total = total + objects.size());

        String scrollId = response.scrollId();

        while (scrollId != null) {
            final String searchScroll = scrollId;
            ScrollResponse<Object> scrollResponse = client.scroll(
                    ScrollRequest.of(scroll -> scroll
                            .scrollId(searchScroll)
                            .scroll(time -> time.time("2m"))
                    ), Object.class
            );

            List<Hit<Object>> hitList = scrollResponse.hits().hits();
            if (hitList.isEmpty()) {
                break;
            } else {
                scrollId = scrollResponse.scrollId();
            }

            List<Object> scrollObjects = hitList.stream().map(Hit::source).collect(Collectors.toList());
            dataConsumer.accept(scrollObjects);
            log.info("scroll search size:{}", total = total + scrollObjects.size());
        }
        log.info("finish scroll search handle total :{}", total);
    }

    /**
     * scroll查询出数据，然后根据需要处理
     *
     * @param client       ElasticsearchClient
     * @param query        Query.of()
     * @param index        index
     * @param dataConsumer 数据消费处理函数
     * @throws IOException IOException
     */
    public static void scrollHandle(ElasticsearchClient client, Query query, String index, Consumer<List<Object>> dataConsumer) throws IOException {
        val indexList = getSearchIndexList(index);
        scrollHandle(client, query, indexList, dataConsumer);
    }

    /**
     * index -> list<index>
     *
     * @param index index
     * @return list index
     */
    public static List<String> getSearchIndexList(String index) {
        val indexList = new ArrayList<String>();
        if (index.contains(COMMA)) {
            indexList.addAll(StrUtil.split(index, COMMA));
        } else {
            indexList.add(index);
        }
        return indexList;
    }

    /**
     * 查询count
     *
     * @param client client
     * @param query  query
     * @param index  index
     * @return count
     */
    public static Long getCount(ElasticsearchClient client, Query query, List<String> index) {
        try {
            CountResponse countResponse = client.count(CountRequest.of(count -> count
                            .index(index)
                            .query(query)
                    )
            );
            return countResponse.count();

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }
}
