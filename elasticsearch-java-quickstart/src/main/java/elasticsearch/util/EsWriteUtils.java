package elasticsearch.util;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Pair;
import cn.hutool.core.map.MapUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import elasticsearch.annotation.ESField;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zhangxuecheng4441
 * @date 2022/10/26/026 10:14
 */
@Slf4j
public class EsWriteUtils {

    /**
     * 写入对象规范
     */
    public interface EsDoc {
        /**
         * 获取id
         *
         * @return id
         */
        String getDocId();

        /**
         * 获取routing
         *
         * @return routing
         */
        String getRouting();
    }

    /**
     * 数据写入
     *
     * @param client    client
     * @param indexName index name
     * @param bean      doc 注意写入数据受@ESField过滤
     */
    public static void write(ElasticsearchClient client, String indexName, EsDoc bean) {
        BulkResponse bulkResponse;
        try {
            bulkResponse = client.bulk(bulkBd -> bulkBd
                    .operations(bulkOpBd -> bulkOpBd
                            .update(bulkOp -> bulkOp
                                    .index(indexName)
                                    .id(bean.getDocId())
                                    .routing(bean.getRouting())
                                    .action(upAction -> upAction
                                            .docAsUpsert(true)
                                            .doc(ESField.Method.getMap(bean))
                                    )
                            )
                    )
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (bulkResponse == null || bulkResponse.errors()) {
            log.error("error bulk es doc({}) and error:{}", bean.getDocId(), bulkResponse);
        } else {
            log.info("write to {} doc success id:{}", indexName, bean.getDocId());
        }
    }


    /**
     * 数据批量写入
     *
     * @param client    client
     * @param indexName indexName
     * @param esDocs    List<? extends EsDoc> 注意写入数据受@ESField过滤
     */
    public static void batchWrite(ElasticsearchClient client, String indexName, List<? extends EsDoc> esDocs) {
        BulkResponse bulkResponse;
        try {
            final int batch = 1000;
            for (List<? extends EsDoc> beans : ListUtil.partition(esDocs, batch)) {
                List<BulkOperation> operations = beans.stream()
                        .map(bean -> BulkOperation.of(bulk -> bulk
                                        .update(up -> up
                                                .index(indexName)
                                                .id(bean.getDocId())
                                                .routing(bean.getRouting())
                                                .action(upAction -> upAction
                                                        .docAsUpsert(true)
                                                        .doc(ESField.Method.getMap(bean))
                                                )
                                        )
                                )
                        )
                        .collect(Collectors.toList());

                bulkResponse = client.bulk(bulkBd -> bulkBd
                        .index(indexName)
                        .operations(operations)
                );

                //client.indices().flush()

                if (bulkResponse == null || bulkResponse.errors()) {
                    log.error("error bulk es doc size:({}) and error:{}", beans.size(), bulkResponse);
                } else {
                    log.info("success branch update index :{} total:{}", indexName, beans.size());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 数据批量写入
     *
     * @param client    client
     * @param indexName indexName
     * @param esDocs    List<Pair<String, Map<String,Object>>>
     */
    public static void batchMapWrite(ElasticsearchClient client, String indexName, List<Pair<String, Map<String, Object>>> esDocs) {
        BulkResponse bulkResponse;
        try {
            final int batch = 1000;
            for (List<Pair<String, Map<String, Object>>> beans : ListUtil.partition(esDocs, batch)) {
                List<BulkOperation> operations = beans.stream()
                        .map(bean -> {
                                    val docMap = bean.getValue();
                                    val doc = MapUtil.removeNullValue(docMap);
                                    return BulkOperation.of(bulk -> bulk
                                            .update(up -> up
                                                    .index(indexName)
                                                    .id(bean.getKey())
                                                    .routing(bean.getKey())
                                                    .action(upAction -> upAction
                                                            .docAsUpsert(true)
                                                            .doc(doc)
                                                    )
                                            )
                                    );
                                }
                        )
                        .collect(Collectors.toList());

                bulkResponse = client.bulk(bulkBd -> bulkBd
                        .index(indexName)
                        .operations(operations)
                );

                if (bulkResponse == null || bulkResponse.errors()) {
                    log.error("error bulk es doc size:({}) and error:{}", beans.size(), bulkResponse);
                } else {
                    log.info("success branch update index :{} total:{}", indexName, beans.size());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
