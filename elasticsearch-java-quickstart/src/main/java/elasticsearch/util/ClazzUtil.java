package elasticsearch.util;

import cn.hutool.core.util.ClassUtil;
import lombok.val;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author zhangxuecheng4441
 * @date 2023/2/16/016 11:25
 */
public class ClazzUtil extends ClassUtil {

    /**
     * 获取所有父类
     * @param clazz clazz
     * @return Set<Class<?>>
     */
    public static Set<Class<?>> getSuperClazz(Class<?> clazz) {
        val clazzList = new HashSet<Class<?>>();

        //递归找父类
        if (clazz.getSuperclass() != null) {
            clazzList.add(clazz);
            clazzList.addAll(getSuperClazz(clazz.getSuperclass()));
        }else {
            clazzList.add(clazz);
        }
        return clazzList;
    }

    /**
     * 获取所有字段
     *
     * @param clazz clazz
     * @return  Set<Field>
     */
    public static Set<Field> getClazzAllField(Class<?> clazz) {
        val superClazz = getSuperClazz(clazz);
        val fields = new HashSet<Field>();

        for (Class<?> aClass : superClazz) {
            fields.addAll(Arrays.asList(ClassUtil.getDeclaredFields(aClass)));
        }

        return fields;
    }
}
