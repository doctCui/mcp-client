package org.springframework.ai.mcp.samples.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.mcp.SyncMcpToolCallback;
 
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
 

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
 
import java.time.Duration;
 
import java.util.List;
 

@Configuration
public class McpClientConfig {


    // @Bean
    // public McpSyncClient amapMcpSyncClient() {
    //     HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
    //     httpClientBuilder.connectTimeout(Duration.ofSeconds(5));
    //     HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    // //    requestBuilder.header("Authorization","Bearer sk-1281a468bfd44334992188e315fe102c");
    //     HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder("https://mcp.amap.com")
    //             .requestBuilder(requestBuilder)
    //             .sseEndpoint("/sse?key=ca50b9c86c4bfbbc41855d9116a488cd")
    //             .clientBuilder(httpClientBuilder).build();

    //     McpSyncClient mcpSyncClient = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
    //     mcpSyncClient.initialize();
    //     return mcpSyncClient;
    // }

//     @Bean
//     public McpSyncClient railwayMcpSyncClient() {
//         HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
//         httpClientBuilder.connectTimeout(Duration.ofSeconds(30));
//         HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
// //        requestBuilder.header("Authorization","Bearer sk-1281a468bfd44334992188e315fe102c");
//         HttpClientSseClientTransport transport = HttpClientSseClientTransport.builder("https://mcp.api-inference.modelscope.net/e856b50679a44b/")
//                 .requestBuilder(requestBuilder)
//                 .sseEndpoint("sse")
//                 .clientBuilder(httpClientBuilder).build();

//         McpSyncClient mcpSyncClient = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
//         mcpSyncClient.initialize();
//         return mcpSyncClient;
//     }


    // @Bean
    // public List<SyncMcpToolCallback> amapToolCallbacks(McpSyncClient amapMcpSyncClient) {
    //     List<SyncMcpToolCallback> list = amapMcpSyncClient
    //             .listTools()
    //             .tools()
    //             .stream()
    //             .map(tool -> new SyncMcpToolCallback(amapMcpSyncClient, tool))
    //             .toList();
    //     return list;
    // }

    // @Bean
    // public List<SyncMcpToolCallback> railwayToolCallbacks(McpSyncClient railwayMcpSyncClient) {
    //     List<SyncMcpToolCallback> list = railwayMcpSyncClient
    //             .listTools()
    //             .tools()
    //             .stream()
    //             .map(tool -> new SyncMcpToolCallback(railwayMcpSyncClient, tool))
    //             .toList();
    //     return list;
    // }

    @Bean
    public McpSyncClient getMcpSyncClient() {
        HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
        httpClientBuilder.connectTimeout(Duration.ofSeconds(5));
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        requestBuilder.header("Authorization","Bearer secret123");
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder("http://localhost:8080")
                .requestBuilder(requestBuilder)
                .endpoint("/mcp")
                .clientBuilder(httpClientBuilder).build();

        McpSyncClient mcpSyncClient = McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
        mcpSyncClient.initialize();
        return mcpSyncClient;
    }


    @Bean
    public McpSyncHttpClientRequestCustomizer correlationHeadersCustomizer() {
        return (requestBuilder, httpMethod, uri, contentType, transportContext) -> {
            String corrId = org.slf4j.MDC.get("corrId");
            if (corrId == null || corrId.isBlank()) {
                corrId = java.util.UUID.randomUUID().toString();
            }
            requestBuilder.header("X-Correlation-Id", corrId);
        };
    }

    @Bean
    public List<SyncMcpToolCallback> getSyncMcpToolCallback(McpSyncClient mcpSyncClient) {
        List<SyncMcpToolCallback> list = mcpSyncClient
                .listTools()
                .tools()
                .stream()
                .map(tool -> new SyncMcpToolCallback(mcpSyncClient, tool))
                .toList();
        return list;
    }

//    @Bean
//    public String systemPrompt(McpSyncClient client){
//        Map<String, Object> arguments = new HashMap<>();
//        arguments.put("user","张三");
//        McpSchema.GetPromptRequest getPromptRequest = new McpSchema.GetPromptRequest("weather",arguments);
//        McpSchema.GetPromptResult prompt = client.getPrompt(getPromptRequest);
//        List<McpSchema.PromptMessage> messages = prompt.messages();
//        McpSchema.PromptMessage promptMessage = messages.get(0);
//        McpSchema.Content content = promptMessage.content();
//     //    return "这是一个地图规划MCP";
//    }

    // 使用显式的内存仓库（InMemoryChatMemoryRepository）和窗口记忆
    @Bean
    public ChatMemoryRepository chatMemoryRepository() {
        return new InMemoryChatMemoryRepository();
    }

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        // 默认挂载内存记忆顾问；工具在请求时按需选择
        return builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

}
