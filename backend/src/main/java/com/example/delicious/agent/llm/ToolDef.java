package com.example.delicious.agent.llm;

import java.util.Map;

/**
 * 工具（Function Calling）定义，对应 OpenAI tools 协议中的一个 function。
 *
 * @param name        工具名（供模型调用）
 * @param description 工具的用途说明（模型据此决定是否调用）
 * @param parameters  参数的 JSON Schema 描述
 */
public record ToolDef(String name, String description, Map<String, Object> parameters) {
}
