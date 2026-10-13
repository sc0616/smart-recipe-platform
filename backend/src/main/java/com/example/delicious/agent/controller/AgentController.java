package com.example.delicious.agent.controller;

import com.example.delicious.agent.config.AgentProperties;
import com.example.delicious.agent.core.ReActAgent;
import com.example.delicious.agent.knowledge.RecipeKnowledgeBase;
import com.example.delicious.agent.llm.OpenAiCompatibleClient;
import com.example.delicious.agent.llm.ToolDef;
import com.example.delicious.agent.memory.ChatMemoryStore;
import com.example.delicious.agent.model.AgentChatRequest;
import com.example.delicious.agent.tools.RecipeTools;
import com.example.delicious.common.Result;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * AI Agent 接口层。
 * <ul>
 *   <li>POST /agent/chat —— SSE 流式对话（打字机效果 + 工具调用事件）</li>
 *   <li>POST /agent/chat/message —— 一次性返回完整回答（非流式兜底）</li>
 *   <li>POST /agent/session/clear —— 清空某会话记忆</li>
 *   <li>GET  /agent/health —— Agent 运行状态与能力探测</li>
 *   <li>POST /agent/knowledge/reindex —— 重建 RAG 知识库（菜谱/食材变更后调用）</li>
 * </ul>
 * 所有接口均需登录（沿用项目既有 JWT 拦截器）。
 */
@RestController
@RequestMapping("/agent")
public class AgentController {

    /** SSE 连接最长保持 30 分钟 */
    private static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L;

    @Resource
    private ReActAgent agent;
    @Resource
    private RecipeKnowledgeBase knowledgeBase;
    @Resource
    private OpenAiCompatibleClient client;
    @Resource
    private ChatMemoryStore memoryStore;
    @Resource
    private RecipeTools recipeTools;
    @Resource
    private AgentProperties props;
    @Resource(name = "agentExecutor")
    private ExecutorService agentExecutor;

    /** SSE 流式对话。 */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String sessionId = resolveSession(request);
        String message = request == null ? null : request.getMessage();
        agentExecutor.submit(() -> agent.run(sessionId, message, emitter));
        return emitter;
    }

    /** 非流式对话（便于脚本/接口测试）。 */
    @PostMapping("/chat/message")
    public Result<Map<String, Object>> chatMessage(@RequestBody AgentChatRequest request) {
        String sessionId = resolveSession(request);
        String answer = agent.reply(sessionId, request == null ? null : request.getMessage());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("answer", answer);
        data.put("mode", client.online() ? "online" : "offline");
        return Result.success(data);
    }

    /** 清空会话记忆。 */
    @PostMapping("/session/clear")
    public Result<?> clearSession(@RequestBody(required = false) AgentChatRequest request) {
        if (request != null && request.getSessionId() != null) {
            memoryStore.clear(request.getSessionId());
        }
        return Result.success();
    }

    /** Agent 运行状态与能力探测。 */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("enabled", props.isEnabled());
        h.put("online", client.online());
        h.put("mode", client.online() ? "online" : "offline");
        h.put("model", props.getModel());
        h.put("baseUrl", props.getBaseUrl());
        h.put("temperature", props.getTemperature());
        h.put("maxIterations", props.getMaxIterations());
        h.put("embeddingEnabled", client.embeddingEnabled());
        h.put("embeddingModel", props.getEmbeddingModel());
        h.put("retrievalMode", client.embeddingEnabled() ? "semantic+keyword" : "keyword");
        h.put("knowledgeChunks", knowledgeBase.chunkCount());
        h.put("activeSessions", memoryStore.sessionCount());

        List<String> toolNames = new ArrayList<>();
        for (ToolDef t : recipeTools.toolDefs()) {
            toolNames.add(t.name());
        }
        h.put("tools", toolNames);
        return Result.success(h);
    }

    /** 重建 RAG 知识库。 */
    @PostMapping("/knowledge/reindex")
    public Result<Map<String, Object>> reindex() {
        knowledgeBase.reindex();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("knowledgeChunks", knowledgeBase.chunkCount());
        data.put("retrievalMode", client.embeddingEnabled() ? "semantic+keyword" : "keyword");
        return Result.success(data);
    }

    private String resolveSession(AgentChatRequest request) {
        if (request != null && request.getSessionId() != null && !request.getSessionId().isBlank()) {
            return request.getSessionId();
        }
        return UUID.randomUUID().toString();
    }
}
