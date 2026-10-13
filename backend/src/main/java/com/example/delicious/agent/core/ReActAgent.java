package com.example.delicious.agent.core;

import com.example.delicious.agent.config.AgentProperties;
import com.example.delicious.agent.llm.OpenAiCompatibleClient;
import com.example.delicious.agent.llm.ToolDef;
import com.example.delicious.agent.memory.ChatMemoryStore;
import com.example.delicious.agent.tools.RecipeTools;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct 智能体核心（Reason + Act 循环）。
 * <p>
 * 每一轮：把「系统提示 + 会话记忆 + 用户问题」发给大模型 →
 * 模型要么直接回答（Final Answer），要么返回 tool_calls（Action）→
 * 本地执行工具得到 Observation 回灌给模型 → 继续下一轮，直到得到最终答案或达到轮数上限。
 * <p>
 * 关键工程特性：
 * <ul>
 *   <li>无 API Key 时自动降级为「离线规则模式」，仍可基于站内数据作答；</li>
 *   <li>事件通过 {@link AgentEventSink} 抽象输出，同一套逻辑同时支持 SSE 流式与一次性返回；</li>
 *   <li>工具执行结果一律为 JSON，模型据此继续推理，避免幻觉。</li>
 * </ul>
 */
@Service
public class ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final OpenAiCompatibleClient client;
    private final RecipeTools recipeTools;
    private final ChatMemoryStore memory;
    private final AgentProperties props;
    private final ObjectMapper mapper = new ObjectMapper();

    public ReActAgent(OpenAiCompatibleClient client,
                      RecipeTools recipeTools,
                      ChatMemoryStore memory,
                      AgentProperties props) {
        this.client = client;
        this.recipeTools = recipeTools;
        this.memory = memory;
        this.props = props;
    }

    /** SSE 流式入口（在后台线程中调用）。 */
    public void run(String sessionId, String userMessage, SseEmitter emitter) {
        try {
            execute(sessionId, userMessage, new SseEmitterSink(emitter));
        } finally {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 客户端已断开
            }
        }
    }

    /** 非流式入口：一次性返回完整回答。 */
    public String reply(String sessionId, String userMessage) {
        StringBuilderSink sink = new StringBuilderSink();
        execute(sessionId, userMessage, sink);
        return sink.text();
    }

    // ==================== 核心执行流程 ====================

    private void execute(String sessionId, String userMessage, AgentEventSink sink) {
        try {
            if (userMessage == null || userMessage.isBlank()) {
                sink.status("请先输入你的问题～");
                return;
            }
            if (!client.online()) {
                offline(sessionId, userMessage, sink);
            } else {
                online(sessionId, userMessage, sink);
            }
        } catch (Exception e) {
            log.warn("Agent 执行异常：{}", e.getMessage());
            sink.error("Agent 处理失败：" + e.getMessage());
        } finally {
            sink.done();
        }
    }

    /** 在线模式：真正的 ReAct 工具调用循环。 */
    private void online(String sessionId, String userMessage, AgentEventSink sink) throws Exception {
        List<ToolDef> tools = recipeTools.toolDefs();

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(message("system", buildSystemPrompt()));
        messages.addAll(memory.getMessages(sessionId, props.getMemoryWindow()));
        messages.add(message("user", userMessage));

        int maxRounds = Math.max(1, props.getMaxIterations());
        for (int round = 0; round < maxRounds; round++) {
            sink.status(round == 0 ? "正在思考…" : "正在根据工具结果继续推理…");

            JsonNode response = client.chat(messages, tools);
            JsonNode choices = response.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                sink.error("大模型没有返回有效内容");
                return;
            }
            JsonNode msg = choices.get(0).path("message");

            JsonNode contentNode = msg.path("content");
            String content = (contentNode.isMissingNode() || contentNode.isNull()) ? null : contentNode.asText();
            JsonNode toolCalls = msg.path("tool_calls");

            // ---- Action：模型要求调用工具 ----
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                messages.add(mapper.convertValue(msg, MAP_TYPE));
                for (JsonNode tc : toolCalls) {
                    String callId = tc.path("id").asText();
                    String fnName = tc.path("function").path("name").asText();
                    String fnArgs = tc.path("function").path("arguments").asText("");
                    log.info("Agent 调用工具：{} 参数={}", fnName, fnArgs);

                    sink.tool(fnName, "start");
                    String result = recipeTools.dispatch(fnName, parseArgs(fnArgs));
                    messages.add(toolMessage(callId, fnName, result));
                    sink.tool(fnName, "end");
                }
                continue;
            }

            // ---- Final Answer：模型给出最终回答 ----
            if (content != null && !content.isBlank()) {
                memory.addMessage(sessionId, "user", userMessage, props.getMemoryWindow());
                memory.addMessage(sessionId, "assistant", content, props.getMemoryWindow());
                sink.tokenStream(content);
                return;
            }
            break;
        }

        String fallback = "抱歉，这个问题我暂时没能想清楚。你可以换个说法，或者直接告诉我你想吃的菜、手上有哪些食材，我来帮你查。";
        memory.addMessage(sessionId, "user", userMessage, props.getMemoryWindow());
        memory.addMessage(sessionId, "assistant", fallback, props.getMemoryWindow());
        sink.tokenStream(fallback);
    }

    /** 离线模式：不依赖大模型，用站内检索 + 规则给出可用回答。 */
    private void offline(String sessionId, String userMessage, AgentEventSink sink) {
        sink.status("离线规则模式：未配置大模型，使用站内检索作答");
        String reply = buildOfflineReply(userMessage);
        memory.addMessage(sessionId, "user", userMessage, props.getMemoryWindow());
        memory.addMessage(sessionId, "assistant", reply, props.getMemoryWindow());
        sink.tokenStream(reply);
    }

    private String buildOfflineReply(String rawMessage) {
        String q = rawMessage == null ? "" : rawMessage.trim();
        StringBuilder sb = new StringBuilder();
        sb.append("🧠 当前是「离线规则模式」——还没有配置大模型 API Key，我先用站内数据帮你找答案。\n\n");

        boolean answered = false;

        if (containsAny(q, "多少", "统计", "数量", "几个", "总数")) {
            String stats = recipeTools.humanSiteStats();
            if (!stats.isBlank()) {
                sb.append("📊 本站数据：").append(stats).append("。\n\n");
                answered = true;
            }
        }

        if (containsAny(q, "分类", "类别", "种类")) {
            String classes = recipeTools.humanListClassifications();
            if (!classes.isBlank()) {
                sb.append("🗂️ 现有菜谱分类：\n").append(classes).append("\n");
                answered = true;
            }
        }

        String recipes = recipeTools.humanSearchRecipes(q, 3);
        if (!recipes.isBlank()) {
            sb.append("🍽️ 找到这些相关菜谱：\n").append(recipes).append("\n");
            answered = true;
        }

        String materials = recipeTools.humanSearchMaterials(q, 3);
        if (!materials.isBlank()) {
            sb.append("🥬 相关食材：\n").append(materials).append("\n");
            answered = true;
        }

        if (!answered) {
            sb.append("抱歉，我在站内没有找到和「").append(q).append("」直接相关的内容。\n\n");
            sb.append("你可以试试这样问我：\n");
            sb.append("- 站内一共有多少道菜谱？\n");
            sb.append("- 有哪些菜谱分类？\n");
            sb.append("- 红烧肉怎么做？\n");
            sb.append("- 我有土豆和牛肉，能做什么菜？\n");
        }

        sb.append("\n💡 在 `application.yml` 中配置 `ai.base-url` / `ai.api-key` / `ai.model`");
        sb.append("（支持 DeepSeek、通义千问、OpenAI、Ollama）后，我就能真正「思考 + 调用工具」了。");
        return sb.toString();
    }

    private String buildSystemPrompt() {
        return """
                你是「美食菜谱」网站的 AI 美食智能体（Agent）。你具备工具调用（Function Calling）能力，\
                可以查询站内真实的菜谱、食材、分类与统计数据。

                工作准则：
                1. 凡是涉及站内数据（菜谱名称、做法、食材、分类、数量统计）的问题，必须先调用工具获取真实数据，严禁凭空编造。
                2. 当用户说「我有 XX 和 YY，能做什么菜」时，调用 recommend_recipes 工具。
                3. 当用户想浏览或搜索菜谱时，调用 search_recipes；需要完整做法时，再调用 get_recipe_detail。
                4. 涉及烹饪常识、食材搭配、做法要点等知识性问题时，调用 search_knowledge 检索站内知识库。
                5. 若一次工具调用不足以回答，可以连续调用多个工具，直到信息足够。
                6. 用简体中文回答，语气亲切、简洁、有条理；使用 Markdown 组织内容（列表、加粗、小标题）。
                7. 如果工具没有返回结果，请如实告知用户，不要编造菜谱或做法。
                """;
    }

    // ==================== 消息构造 ====================

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static Map<String, Object> toolMessage(String callId, String name, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", "tool");
        m.put("tool_call_id", callId);
        m.put("name", name);
        m.put("content", content);
        return m;
    }

    private Map<String, Object> parseArgs(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = mapper.readValue(json, MAP_TYPE);
            return parsed == null ? new LinkedHashMap<>() : parsed;
        } catch (Exception e) {
            log.warn("工具参数解析失败：{}", json);
            return new LinkedHashMap<>();
        }
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String k : keywords) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 事件输出抽象 ====================

    /** 把 Agent 的运行过程抽象为事件，屏蔽「SSE 流式」与「一次性返回」的差异。 */
    public interface AgentEventSink {
        /** 运行状态提示（如「正在思考…」） */
        void status(String text);

        /** 工具调用事件 */
        void tool(String name, String status);

        /** 输出一小段文本 */
        void token(String text);

        /** 错误信息 */
        void error(String text);

        /** 结束标记 */
        void done();

        /** 默认实现：把整段文本切分为若干小块依次输出，形成打字机效果。 */
        default void tokenStream(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            int chunkSize = 6;
            for (int i = 0; i < text.length(); i += chunkSize) {
                token(text.substring(i, Math.min(i + chunkSize, text.length())));
            }
        }
    }

    /** SSE 实现：通过 SseEmitter 推送事件。 */
    private static final class SseEmitterSink implements AgentEventSink {
        private final SseEmitter emitter;
        private final ObjectMapper mapper = new ObjectMapper();

        SseEmitterSink(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void status(String text) {
            send("status", encode(text));
        }

        @Override
        public void tool(String name, String status) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("status", status);
            try {
                send("tool", mapper.writeValueAsString(m));
            } catch (Exception e) {
                send("tool", encode(name));
            }
        }

        @Override
        public void token(String text) {
            send("token", encode(text));
            try {
                Thread.sleep(8);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void error(String text) {
            send("error", encode(text));
        }

        @Override
        public void done() {
            send("done", "{}");
        }

        /**
         * 把文本编码为 JSON 字符串下发。
         * <p>
         * 必要性：SSE 以空行分帧，若 data 中直接包含换行符会破坏帧结构、导致前端丢字。
         * JSON 编码会把 \n 转义为 \\n，从根本上规避该问题。
         */
        private String encode(String text) {
            try {
                return mapper.writeValueAsString(text == null ? "" : text);
            } catch (Exception e) {
                return "\"\"";
            }
        }

        private void send(String event, String data) {
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (Exception ignored) {
                // 客户端断开时忽略
            }
        }
    }

    /** 非流式实现：把 token 累积为完整字符串。 */
    private static final class StringBuilderSink implements AgentEventSink {
        private final StringBuilder sb = new StringBuilder();

        @Override
        public void status(String text) {
            // 非流式模式无需状态提示
        }

        @Override
        public void tool(String name, String status) {
            // 非流式模式无需工具事件
        }

        @Override
        public void token(String text) {
            sb.append(text);
        }

        @Override
        public void error(String text) {
            sb.append("\n【错误】").append(text);
        }

        @Override
        public void done() {
            // 无需处理
        }

        String text() {
            return sb.toString();
        }
    }
}
