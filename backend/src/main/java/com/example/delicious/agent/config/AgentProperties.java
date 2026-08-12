package com.example.delicious.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI Agent 配置项（application.yml 中的 ai.* 前缀）。
 * <p>
 * 采用 OpenAI 兼容协议，因此可对接任意兼容服务：
 * <ul>
 *   <li>DeepSeek：base-url = https://api.deepseek.com，model = deepseek-chat</li>
 *   <li>通义千问：base-url = https://dashscope.aliyuncs.com/compatible-mode/v1，model = qwen-plus</li>
 *   <li>OpenAI：base-url = https://api.openai.com/v1，model = gpt-4o-mini</li>
 *   <li>Ollama（本地）：base-url = http://localhost:11434/v1，model = qwen2.5 等</li>
 * </ul>
 * 未配置 api-key / base-url / model 任一缺失时，Agent 自动降级为「离线规则模式」，不依赖任何外部服务。
 */
@Data
@ConfigurationProperties(prefix = "ai")
public class AgentProperties {

    /** Agent 总开关 */
    private boolean enabled = true;

    /** OpenAI 兼容服务地址，例如 https://api.deepseek.com */
    private String baseUrl = "";

    /** API Key；留空则进入离线规则模式 */
    private String apiKey = "";

    /** 对话模型名，例如 deepseek-chat / qwen-plus / gpt-4o-mini */
    private String model = "";

    /** 向量化模型名，例如 text-embedding-v3 / text-embedding-3-small；留空则使用关键词检索 */
    private String embeddingModel = "";

    /** 向量化服务地址，默认复用 baseUrl */
    private String embeddingBaseUrl = "";

    /** 采样温度，越低越稳定 */
    private double temperature = 0.4;

    /** 单次生成最大 token 数 */
    private int maxTokens = 1200;

    /** ReAct 工具调用最大轮数（防止死循环） */
    private int maxIterations = 6;

    /** 每个会话保留的最近消息条数 */
    private int memoryWindow = 10;

    /** 会话记忆过期时间（分钟） */
    private long memoryTtlMinutes = 30;

    /** 单次 LLM 请求超时（秒） */
    private int timeoutSeconds = 60;

    /** RAG 检索返回条数 */
    private int ragTopK = 3;
}
