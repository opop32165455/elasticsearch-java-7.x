package elasticsearch.pojo;

/**
 * 写入对象规范
 *
 * @author zhangxuecheng4441
 * @date 2023/2/27/027 10:06
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
