package com.example.delicious.agent.memory;

import com.example.delicious.agent.config.AgentProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆（Chat Memory）。
 * <p>
 * 以 sessionId 为键在内存中保存最近若干轮对话，超出 {@code ai.memory-window} 自动淘汰最早的消息，
 * 超过 {@code ai.memory-ttl-minutes} 未访问的会话自动清理。这正是多轮对话「记得上下文」的来源。
 */
@Service
public class ChatMemoryStore {

    /** 一条记忆消息 */
    public record MemoryMessage(String role, String content) {
    }

    private final AgentProperties props;
    private final Map<String, Deque<MemoryMessage>> store = new ConcurrentHashMap<>();
    private final Map<String, Long> lastAccess = new ConcurrentHashMap<>();

    public ChatMemoryStore(AgentProperties props) {
        this.props = props;
    }

    /** 读取某会话的记忆（已按窗口裁剪，可直接作为 LLM messages 前缀）。 */
    public List<Map<String, Object>> getMessages(String sessionId, int window) {
        if (sessionId == null || sessionId.isBlank()) {
            return new ArrayList<>();
        }
        touch(sessionId);
        Deque<MemoryMessage> deque = store.get(sessionId);
        if (deque == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        synchronized (deque) {
            for (MemoryMessage m : deque) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("role", m.role());
                map.put("content", m.content());
                out.add(map);
            }
        }
        if (out.size() > window) {
            return new ArrayList<>(out.subList(out.size() - window, out.size()));
        }
        return out;
    }

    /** 追加一条记忆并按窗口裁剪。 */
    public void addMessage(String sessionId, String role, String content, int window) {
        if (sessionId == null || sessionId.isBlank() || content == null) {
            return;
        }
        Deque<MemoryMessage> deque = store.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new MemoryMessage(role, content));
            while (deque.size() > window) {
                deque.removeFirst();
            }
        }
        lastAccess.put(sessionId, System.currentTimeMillis());
    }

    /** 清空某会话记忆（前端「新建会话」时调用）。 */
    public void clear(String sessionId) {
        if (sessionId == null) {
            return;
        }
        store.remove(sessionId);
        lastAccess.remove(sessionId);
    }

    /** 当前活跃会话数。 */
    public int sessionCount() {
        return store.size();
    }

    private void touch(String sessionId) {
        long now = System.currentTimeMillis();
        Long last = lastAccess.get(sessionId);
        long ttl = Math.max(1, props.getMemoryTtlMinutes()) * 60_000L;
        if (last != null && now - last > ttl) {
            clear(sessionId);
            return;
        }
        lastAccess.put(sessionId, now);
    }
}
