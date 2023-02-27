package elasticsearch.annotation;

import elasticsearch.util.ClazzUtil;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;

/**
 * es字段,实体字段只有标明了该字段，才能够写入到es中
 *
 * @author zhangxuecheng4441
 * @date 2022/8/12/012 13:42
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface ESField {


    @Slf4j
    class Method {
        /**
         * bean 转 map 受ESField字段过滤
         *
         * @param obj obj
         * @param <T> obj T
         * @return obj map
         */
        public static <T> Map<String, Object> getMap(T obj) {
            ESField esAnno = obj.getClass().getAnnotation(ESField.class);
            //类上包含注解 所有字段写入es
            final boolean matchAllField = esAnno != null;
            val fields = ClazzUtil.getClazzAllField(obj.getClass());

            String errorFieldName = "serialVersionUID";
            return fields.stream()
                    .peek(field -> field.setAccessible(true))
                    //类或者字段上包含注解 则写入es
                    .filter(field -> matchAllField || field.getAnnotation(ESField.class) != null)
                    .filter(field -> !errorFieldName.equals(field.getName()))
                    .collect(HashMap::new,
                            (map, field) -> {
                                try {
                                    if (field.get(obj) != null) {
                                        map.put(field.getName(), field.get(obj));
                                    }
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }
                            },
                            HashMap::putAll);
        }
    }
}
