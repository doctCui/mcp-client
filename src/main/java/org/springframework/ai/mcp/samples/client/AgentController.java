package org.springframework.ai.mcp.samples.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
// 使用简单内存记忆可选：如果未引入 spring-ai-memory 依赖，则先移除
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
public class AgentController {

    private final ChatClient chatClient;

//    private final String systemPrompt;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final java.util.List<SyncMcpToolCallback> amapToolCallbacks;
    private final List<SyncMcpToolCallback> railwayToolCallbacks;


    public AgentController(ChatClient chatClient,
                           @Qualifier("amapToolCallbacks") java.util.List<SyncMcpToolCallback> amapToolCallbacks,
                           @Qualifier("railwayToolCallbacks") List<SyncMcpToolCallback> railwayToolCallbacks) {
        this.chatClient = chatClient;
        this.amapToolCallbacks = amapToolCallbacks;
        this.railwayToolCallbacks = railwayToolCallbacks;
    }

    @GetMapping(value = "/api")
    public String streamResponse(@RequestParam String username) {
        String string = chatClient.prompt(username)
                .advisors(new SimpleLoggerAdvisor())

                .call().content();
        return string;
    }

    @PostMapping(value = "/chat")
    public Mono<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String mcp = request.getOrDefault("mcp", "amap"); // amap | railway
        String sessionId = request.getOrDefault("sessionId", "default");
        if (message == null || message.trim().isEmpty()) {
            return Mono.just(Map.of("error", "请输入消息"));
        }

        // 根据 mcp 选择不同的结构化输出规范
        String schemaInstruction;
        if ("railway".equalsIgnoreCase(mcp)) {
            schemaInstruction = """
                    请将答案严格输出为 JSON（UTF-8，无多余文本、无注释、无 Markdown 代码块）。\n
                    目标结构：\n
                    {\n
                      \"date\": \"YYYY-MM-DD?\",\n
                      \"origin\": \"string\",\n
                      \"destination\": \"string\",\n
                      \"trains\": [\n
                        {\n
                          \"trainNumber\": \"string\",\n
                          \"fromStation\": \"string\",\n
                          \"toStation\": \"string\",\n
                          \"departureTime\": \"HH:mm\",\n
                          \"arrivalTime\": \"HH:mm\",\n
                          \"durationMinutes\": number,\n
                          \"seatTypes\": [\n
                            { \"type\": \"一等座|二等座|商务座|硬卧|软卧|硬座|无座|动卧|一等卧\", \"priceCNY\": number, \"availability\": \"有|无|候补|未知\" }\n
                          ]\n
                        }\n
                      ]\n
                    }\n
                    要求：
                    1.时间使用 24 小时制；价格单位 元；仅返回 JSON。尽量覆盖全天时段：早(00:00-10:59)/中(11:00-16:59)/晚(17:00-23:59)，按出发时间排序；如候选很多，请在每个时段保留不超过3条代表性车次。\n
                    2.时间相关的query需要通过工具来获取当前的时间，不能以模型的时间为准
                    """;
            // 依赖 MemoryAdvisor 保持上下文，无需手动注入
        } else {
            schemaInstruction = """
                    请将答案严格输出为 JSON（UTF-8，无多余文本、无注释、无 Markdown 代码块）。\n
                    目标结构：\n
                    {\n
                      \"origin\": \"string\",\n
                      \"destination\": \"string\",\n
                      \"routes\": [\n
                        {\n
                          \"name\": \"string\",\n
                          \"totalDurationMinutes\": number,\n
                          \"totalWalkMeters\": number,\n
                          \"steps\": [\n
                            {\n
                              \"type\": \"walk|metro|bus|transfer\",\n
                              \"line\": \"string?\",\n
                              \"from\": \"string?\",\n
                              \"to\": \"string?\",\n
                              \"stops\": number?,\n
                              \"durationMinutes\": number?,\n
                              \"distanceMeters\": number?\n
                            }\n
                          ]\n
                        }\n
                      ]\n
                    }\n
                    要求：单位统一为 分钟/米；无法确定的字段可省略；仅返回 JSON。\n
                    """;
            // 依赖 MemoryAdvisor 保持上下文，无需手动注入
        }

        final String sysInstruction = schemaInstruction;
        return Mono.fromCallable(() -> {
            // 按选择的 MCP 工具集进行请求（railway 懒初始化）
            ToolCallback[] callbacks;
            if ("railway".equalsIgnoreCase(mcp)) {
                callbacks = railwayToolCallbacks.toArray(new ToolCallback[0]);
            } else {
                callbacks = amapToolCallbacks.toArray(new ToolCallback[0]);
            }

            String response = chatClient
                    .prompt()
                    .system( "\n\n" + sysInstruction)
                    .user(message)
                    .advisors(a -> a.advisors(new SimpleLoggerAdvisor()).param("conversationId", sessionId))
                    .toolCallbacks(callbacks)
                    .call()
                    .content();

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(response, Map.class);
                // 记忆由 Spring AI Memory 管理，这里不再手动维护
                return Map.of("mcp", mcp, "data", parsed, "sessionId", sessionId);
            } catch (Exception parseError) {
                return Map.of(
                        "mcp", mcp,
                        "raw", response,
                        "error", "模型未按JSON返回，已回退原文",
                        "sessionId", sessionId
                );
            }
        });
    }
}
