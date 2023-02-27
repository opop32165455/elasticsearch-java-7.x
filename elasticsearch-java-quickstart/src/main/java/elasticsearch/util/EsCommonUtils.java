package elasticsearch.util;

import cn.hutool.core.collection.ListUtil;
import co.elastic.clients.elasticsearch._types.FieldValue;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author zxc
 * @date 2022/5/26/026 16:03
 */
public class EsCommonUtils {
    /**
     * string  转 List<FieldValue>
     *
     * @param fields strings
     * @return List<FieldValue>
     */
    public static List<FieldValue> str2Field(String... fields) {
        return strList2Field(ListUtil.toList(fields));
    }

    /**
     * string list 转 List<FieldValue>
     *
     * @param fields string list
     * @return List<FieldValue>
     */
    public static List<FieldValue> strList2Field(Collection<String> fields) {
        return fields.stream()
                .map(FieldValue::of)
                .collect(Collectors.toList());
    }
}
