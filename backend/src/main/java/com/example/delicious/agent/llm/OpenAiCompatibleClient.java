package com.example.delicious.agent.llm;

import com.example.delicious.agent.config.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的轻量大模型网关（零第三方依赖，仅用 JDK HttpClient + Jackson）。
 * <p>
 * 同时提供：对话补全（chat.completions，含 tools/function calling）与文本向量化（embeddings）。
 * 由于走标准协议，DeepSeek / 通义千问 / OpenAI / Ollama 等只需改配置即可切换。
 */
@Component
public class OpenAiCompatibleClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final AgentProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient;

    public OpenAiCompatibleClient(AgentProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** 是否具备调用真实大模型的条件（四项配置齐全且总开关打开）。 */
    public boolean online() {
        return props.isEnabled()
                && isNotBlank(props.getBaseUrl())
                && isNotBlank(props.getApiKey())
                && isNotBlank(props.getModel());
    }

    /** 是否启用语义向量检索。 */
    public boolean embeddingEnabled() {
        return online() && isNotBlank(props.getEmbeddingModel());
    }

    /**
     * 非流式对话补全。返回 OpenAI 兼容的原始响应 JSON（调用方解析 choices[0].message）。
     *
     * @param messages 对话消息列表（role/content，或 tool 消息）
     * @param tools    可用工具；为 null 或空则不启用 function calling
     */
    public JsonNode chat(List<Map<String, Object>> messages, List<ToolDef> tools) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.getModel());
        body.put("temperature", props.getTemperature());
        body.put("max_tokens", props.getMaxTokens());

        ArrayNode msgs = body.putArray("messages");
        for (Map<String, Object> m : messages) {
            msgs.add(mapper.valueToTree(m));
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolArr = body.putArray("tools");
            for (ToolDef t : tools) {
                ObjectNode wrapper = toolArr.addObject();
                wrapper.put("type", "function");
                ObjectNode fn = wrapper.putObject("function");
                fn.put("name", t.name());
                fn.put("description", t.description());
                fn.set("parameters", mapper.valueToTree(t.parameters()));
            }
            body.put("tool_choice", "auto");
        }

        HttpResponse<String> resp = post(chatUrl(), mapper.writeValueAsString(body));
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("大模型返回 " + resp.statusCode() + "：" + truncate(resp.body(), 300));
        }
        return mapper.readTree(resp.body());
    }

    /**
     * 文本向量化，支持批量。返回与入参顺序一致的向量列表。
     */
    public List<float[]> embed(List<String> texts) throws Exception {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.getEmbeddingModel());
        if (texts.size() == 1) {
            body.put("input", texts.get(0));
        } else {
            ArrayNode arr = body.putArray("input");
            texts.forEach(arr::add);
        }

        HttpResponse<String> resp = post(embeddingUrl(), mapper.writeValueAsString(body));
        if (resp.statusCode() >= 400) {
            throw new IllegalStateException("向量化服务返回 " + resp.statusCode() + "：" + truncate(resp.body(), 300));
        }
        JsonNode root = mapper.readTree(resp.body());
        List<float[]> result = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            JsonNode emb = item.path("embedding");
            float[] v = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) {
                v[i] = (float) emb.get(i).asDouble();
            }
            result.add(v);
        }
        return result;
    }

    private HttpResponse<String> post(String url, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + props.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String chatUrl() {
        return join(props.getBaseUrl(), "/chat/completions");
    }

    private String embeddingUrl() {
        String base = isNotBlank(props.getEmbeddingBaseUrl()) ? props.getEmbeddingBaseUrl() : props.getBaseUrl();
        return join(base, "/embeddings");
    }

    private static String join(String base, String path) {
        String b = base == null ? "" : base.trim();
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b + path;
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
