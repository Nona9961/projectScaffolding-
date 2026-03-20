package com.nona.inf.context;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 由Spring管理的线程上下文，可以理解为一个ThreadLocal。 在bean中注入该类其实是一个proxy，每个线程之间是隔离的，和注入HttpServletRequest类似。
 *
 * @author nona
 */
@RequestScope
@Component
@Data
public class ThreadContext {
    private String tenantID;
    private Map<Long, Object> snapshots = new ConcurrentHashMap<>(8);   // 假设每条线程最多有6个快照，8 * 0。75 = 6
    private Map<String, Object> attributes = new ConcurrentHashMap<>(4);  // 通用属性存储（如 UnitOfWork）

    public void saveSnapshot(Long id, Object root) {
        snapshots.put(id, root);
    }

    @SuppressWarnings("unchecked")
    public <T> T getSnapshot(Long id, TypeReference<T> reference) {
        return (T) snapshots.get(id);
    }

    /**
     * 设置属性
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 移除属性
     */
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

}
