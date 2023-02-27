package elasticsearch.util;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.aggregations.*;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.var;

import java.io.IOException;
import java.util.*;

/**
 * @author zxc
 * @date 2022/9/2/002 15:14
 */
@Slf4j
public class EsAggUtils {

    /**
     * terms agg
     *
     * @param field field
     * @param topN  topN
     * @return Aggregation
     */
    public static Aggregation termsAgg(String field, Integer topN) {
        return Aggregation.of(agg -> agg
                .terms(ts -> ts
                        .field(field)
                        .size(topN)
                )
        );
    }

    /**
     * sum agg
     *
     * @param field field
     * @return Aggregation
     */
    public static Aggregation sumAgg(String field) {
        return Aggregation.of(agg -> agg
                .sum(sum -> sum
                        .field(field)
                )

        );
    }

    /**
     * term嵌套2层
     *
     * @param field1 field1
     * @param topN1  topN1
     * @param field2 field2
     * @param topN2  topN2
     * @return Aggregation
     */
    public static Aggregation termsTermsAgg(String field1, Integer topN1, String field2, Integer topN2) {
        return Aggregation.of(
                agg -> agg.terms(
                                ts -> ts.field(field1)
                                        .size(topN1)
                        )
                        .aggregations(field2, termsAgg(field2, topN2))
        );
    }

    /**
     * terms agg
     *
     * @param field    field
     * @param sumField sumField
     * @param topN     topN
     * @return Aggregation
     */
    public static Aggregation termsSumAgg(String field, String sumField, Integer topN) {
        return Aggregation.of(agg -> agg
                .terms(ts -> ts
                        .field(field)
                        .size(topN)
                )
                .aggregations(sumField, sumAgg(sumField))
        );
    }

    /**
     * term 嵌套三层
     *
     * @param field1
     * @param topN1
     * @param field2
     * @param topN2
     * @param field3
     * @param topN3
     * @return
     */
    public static Aggregation terms3Agg(String field1, Integer topN1, String field2, Integer topN2, String field3, Integer topN3) {
        return Aggregation.of(
                agg -> agg.terms(
                        ts -> ts.field(field1)
                                .size(topN1)
                ).aggregations(field2, Aggregation.of(
                                agg1 -> agg1.terms(
                                        ts1 -> ts1.field(field2).size(topN2)
                                ).aggregations(field3, Aggregation.of(
                                                agg2 -> agg2.terms(
                                                        ts2 -> ts2.field(field3).size(topN3)
                                                )

                                        )
                                )
                        )
                )
        );
    }

    /**
     * 简单的terms agg
     *
     * @param client     client
     * @param query      query
     * @param termsField termsField
     * @param topN       topN
     * @param indexes    索引
     * @return HashMap<terms, count>
     */
    public static HashMap<String, Long> termsAgg(ElasticsearchClient client, Query query, String termsField, Integer topN, List<String> indexes) {
        try {
            topN = ObjectUtil.defaultIfNull(topN, 10000);
            HashMap<String, Long> aggMap = new LinkedHashMap<>((int) (topN / 0.75) + 1);
            //term agg
            Aggregation aggregation = termsAgg(termsField, topN);
            SearchRequest searchRequest = new SearchRequest.Builder()
                    .index(indexes)
                    .query(query)
                    .aggregations(termsField, aggregation)
                    .build();

            //search
            SearchResponse<Object> response = client.search(searchRequest, Object.class);

            var termsBuckets = response.aggregations()
                    .get(termsField)
                    .sterms()
                    .buckets()
                    .array();

            //result
            for (var termsBucket : termsBuckets) {
                aggMap.put(termsBucket.key(), termsBucket.docCount());
            }

            return aggMap;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }


    /**
     * 简单的terms agg
     *
     * @param client     client
     * @param query      query
     * @param termsField termsField
     * @param topN       topN
     * @param indexes    索引
     * @return HashMap<terms, count>
     */
    public static HashMap<String, Long> termsAgg(ElasticsearchClient client, Query query, String termsField, Integer topN, String... indexes) {
        return termsAgg(client, query, termsField, topN, Arrays.asList(indexes));
    }


    public static HashMap<String, HashMap<String, Long>> termsTermsAgg(ElasticsearchClient client, Query query, String termsField1, Integer topN1, String termsField2, Integer topN2, String... indexes) {
        try {
            HashMap<String, HashMap<String, Long>> aggMap = new LinkedHashMap<>((int) (topN1 / 0.75) + 1);
            //term term agg
            Aggregation aggregation = termsTermsAgg(termsField1, topN1, termsField2, topN2);

            SearchRequest searchRequest = new SearchRequest.Builder()
                    .index(Arrays.asList(indexes))
                    .query(query)
                    .aggregations(termsField1, aggregation)
                    .build();

            //search
            SearchResponse<Object> response = client.search(searchRequest, Object.class);

            //第一层terms
            Optional.ofNullable(response.aggregations())
                    .map(aggregations -> aggregations.get(termsField1))
                    .map(Aggregate::sterms)
                    .map(StringTermsAggregate::buckets)
                    .map(Buckets::array)
                    .ifPresent(termsBuckets -> {
                        //result
                        for (var termsBucket : termsBuckets) {
                            HashMap<String, Long> subMap = new HashMap<>((int) (topN2 / 0.75) + 1);

                            //第二次terms
                            Optional.ofNullable(termsBucket.aggregations())
                                    .map(aggregations -> aggregations.get(termsField2))
                                    .map(Aggregate::sterms)
                                    .map(StringTermsAggregate::buckets)
                                    .map(Buckets::array)
                                    .ifPresent(terms2Buckets -> {
                                        for (StringTermsBucket bucket2 : terms2Buckets) {
                                            subMap.put(bucket2.key(), bucket2.docCount());
                                        }
                                    });
                            aggMap.put(termsBucket.key(), subMap);
                        }
                    });
            return aggMap;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static HashMap<String, HashMap<String, HashMap<String, Long>>> terms3Agg(ElasticsearchClient client, Query query, String termsField1, Integer topN1, String termsField2, Integer topN2, String termsField3, Integer topN3, String... indexes) {
        try {
            HashMap<String, HashMap<String, HashMap<String, Long>>> res = new LinkedHashMap<>((int) (topN1 / 0.75) + 1);

            //term term agg
            Aggregation aggregation = terms3Agg(termsField1, topN1, termsField2, topN2, termsField3, topN3);

            SearchRequest searchRequest = new SearchRequest.Builder()
                    .index(Arrays.asList(indexes))
                    .query(query)
                    .aggregations(termsField1, aggregation)
                    .build();

            //search
            SearchResponse<Object> response = client.search(searchRequest, Object.class);

            //第一层terms
            Optional.ofNullable(response.aggregations())
                    .map(aggregations -> aggregations.get(termsField1))
                    .map(Aggregate::sterms)
                    .map(StringTermsAggregate::buckets)
                    .map(Buckets::array)
                    .ifPresent(termsBuckets -> {
                        //result
                        for (var bucket : termsBuckets) {
                            HashMap<String, HashMap<String, Long>> bucket2Map = new LinkedHashMap<>((int) (topN2 / 0.75) + 1);

                            //第二次terms
                            Optional.ofNullable(bucket.aggregations())
                                    .map(aggregations -> aggregations.get(termsField2))
                                    .map(Aggregate::sterms)
                                    .map(StringTermsAggregate::buckets)
                                    .map(Buckets::array)
                                    .ifPresent(terms2Buckets -> {
                                        for (StringTermsBucket bucket2 : terms2Buckets) {
                                            HashMap<String, Long> bucket3Map = new LinkedHashMap<>((int) (topN3 / 0.75) + 1);
                                            //第三层terms
                                            Optional.ofNullable(bucket2.aggregations())
                                                    .map(aggregations -> aggregations.get(termsField3))
                                                    .map(Aggregate::sterms)
                                                    .map(StringTermsAggregate::buckets)
                                                    .map(Buckets::array)
                                                    .ifPresent(terms3Bucket -> {
                                                        for (StringTermsBucket bucket3 : terms3Bucket) {
                                                            bucket3Map.put(bucket3.key(), bucket3.docCount());
                                                        }
                                                    });
                                            bucket2Map.put(bucket2.key(), bucket3Map);
                                        }
                                    });
                            res.put(bucket.key(), bucket2Map);
                        }

                    });
            return res;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static LinkedHashMap<String, Object> wrapNAgg(Map<String, Aggregate> aggregateMap) {
        LinkedHashMap<String, Object> resultMap = new LinkedHashMap<>();
        for (Map.Entry<String, Aggregate> entry : aggregateMap.entrySet()) {
            String key = entry.getKey();
            Aggregate aggregate = entry.getValue();
            LinkedHashMap<String, Object> singleResults = new LinkedHashMap<>();
            if (aggregate.isSterms()) {
                for (StringTermsBucket bucket : aggregate.sterms().buckets().array()) {
                    if (CollUtil.isEmpty(bucket.aggregations())) {
                        singleResults.put(bucket.key(), bucket.docCount());
                    } else {
                        singleResults.put(bucket.key(), wrapNAgg(bucket.aggregations()));
                    }
                }
            } else if (aggregate.isLterms()) {
                for (LongTermsBucket bucket : aggregate.lterms().buckets().array()) {
                    if (CollUtil.isEmpty(bucket.aggregations())) {
                        singleResults.put(bucket.key(), bucket.docCount());
                    } else {
                        singleResults.put(bucket.key(), wrapNAgg(bucket.aggregations()));
                    }
                }
            } else if (aggregate.isSum()) {
                singleResults.put(key, aggregate.sum().value());
            } else if (aggregate.isDateHistogram()) {
                List<DateHistogramBucket> dateHistogramBuckets = aggregate.dateHistogram().buckets().array();
                LinkedHashMap<String, Long> dateMap = new LinkedHashMap<>((int) (dateHistogramBuckets.size() / 0.75) + 1);
                dateHistogramBuckets.forEach(bucket -> dateMap.put(bucket.keyAsString(), bucket.docCount()));
                resultMap.put(key, dateMap);
                continue;
            }
            resultMap.put(key, singleResults);
        }
        return resultMap;
    }

    /**
     * termsSumAgg
     *
     * @param client      client
     * @param query       query
     * @param aggField    agg一层字段
     * @param aggSumField agg sum 二层字段
     * @param topN        topN
     * @param indexList   indexList
     * @return Map<String, Double> k:agg field v:sum
     */
    public static Map<String, Double> termsSumAgg(ElasticsearchClient client, Query query, String aggField, String aggSumField, Integer topN, List<String> indexList) {
        Map<String, Double> aggMap = new LinkedHashMap<>((int) (topN / 0.75) + 1);
        try {
            //term agg
            Aggregation aggregation = termsSumAgg(aggField, aggSumField, topN);

            SearchRequest searchRequest = new SearchRequest.Builder()
                    .index(indexList)
                    .query(query)
                    .aggregations(aggField, aggregation)
                    .build();

            //search
            SearchResponse<Object> response = client.search(searchRequest, Object.class);

            //第一层terms
            Optional.ofNullable(response.aggregations())
                    .map(aggregations -> aggregations.get(aggField))
                    .map(Aggregate::sterms)
                    .map(StringTermsAggregate::buckets)
                    .map(Buckets::array)
                    .ifPresent(termsBuckets -> {
                        //result
                        for (var termsBucket : termsBuckets) {
                            //sum agg
                            Optional.ofNullable(termsBucket.aggregations())
                                    .map(aggregations -> aggregations.get(aggSumField))
                                    .map(Aggregate::sum)
                                    .map(SingleMetricAggregateBase::value)
                                    .ifPresent(value -> aggMap.put(termsBucket.key(), value));

                        }
                    });
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return aggMap;
    }
}
