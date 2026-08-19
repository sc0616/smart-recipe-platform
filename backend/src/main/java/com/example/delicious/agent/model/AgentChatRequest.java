package com.example.delicious.agent.model;

import lombok.Data;

/**
 * Agent 对话请求体。
 */
@Data
public class AgentChatRequest {

    /** 会话ID，由前端生成并保持，用于多轮记忆；为空时后端自动生成。 */
    private String sessionId;

    /** 用户消息 */
    private String message;
}
