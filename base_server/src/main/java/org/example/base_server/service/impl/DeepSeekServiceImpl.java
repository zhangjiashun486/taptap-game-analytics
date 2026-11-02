package org.example.base_server.service.impl;

import org.example.base_server.entity.deepseek.DeepSeekMessage;
import org.example.base_server.entity.deepseek.DeepSeekRequestBody;
import org.example.base_server.entity.deepseek.DeepSeekResponseBody;
import org.example.base_server.service.DeepSeekService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek 对话服务：
 *   1. 接收上层传入的 system/user 提示词；
 *   2. 调用 deepseek-chat；
 *   3. 未开启、缺少 Key 或调用失败时返回 null，不阻断规则回退。
 */
@Service
public class DeepSeekServiceImpl implements DeepSeekService {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekServiceImpl.class);

    private static final String GAME_ANSWER_PROMPT = """
            你是 TapTap 游戏数据分析平台的问答助手。
            只能依据用户问题和【数据库查询结果】回答，不得编造游戏、评分、数量或标签。
            如果查询结果为空，就明确说明没有找到符合条件的数据。
            请使用简洁、自然的中文回答，控制在 180 字以内，不要输出 Markdown 表格。
            """;

    private final boolean enabled;
    private final String apiKey;
    private final String apiUrl;
    private final String chatModel;
    private final int maxTokens;
    private final RestClient restClient;

    public DeepSeekServiceImpl(
            @Value("${deepseek.enabled:false}") boolean enabled,
            @Value("${deepseek.key:}") String apiKey,
            @Value("${deepseek.url:https://api.deepseek.com/chat/completions}") String apiUrl,
            @Value("${deepseek.model.chat:deepseek-chat}") String chatModel,
            @Value("${deepseek.timeout:30000}") int timeout,
            @Value("${deepseek.max-tokens:800}") int maxTokens) {
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiUrl = apiUrl;
        this.chatModel = chatModel;
        this.maxTokens = maxTokens;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.restClient = RestClient.builder().requestFactory(factory).build();

        log.info("DeepSeek 配置：enabled={}, keyConfigured={}, model={}",
                enabled, !this.apiKey.isBlank(), chatModel);
    }

    @Override
    public boolean isEnabled() {
        return enabled && !apiKey.isBlank() && apiUrl != null && !apiUrl.isBlank();
    }

    @Override
    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", isEnabled());
        status.put("keyConfigured", !apiKey.isBlank());
        status.put("model", chatModel);
        status.put("endpointConfigured", apiUrl != null && !apiUrl.isBlank());
        return status;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (!isEnabled()) {
            return null;
        }

        DeepSeekRequestBody body = new DeepSeekRequestBody(
                chatModel,
                List.of(
                        new DeepSeekMessage("system", systemPrompt),
                        new DeepSeekMessage("user", userPrompt)
                ),
                maxTokens
        );

        try {
            DeepSeekResponseBody response = restClient.post()
                    .uri(apiUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(DeepSeekResponseBody.class);

            if (response == null) {
                log.warn("DeepSeek 返回空响应");
                return null;
            }
            if (response.getError() != null) {
                log.warn("DeepSeek 返回错误：type={}, code={}, message={}",
                        response.getError().getType(),
                        response.getError().getCode(),
                        response.getError().getMessage());
                return null;
            }

            String content = response.firstContent();
            if (content == null || content.isBlank()) {
                log.warn("DeepSeek 未返回有效文本");
                return null;
            }
            if (response.getUsage() != null) {
                log.info("DeepSeek 调用成功：tokens={}", response.getUsage().getTotal_tokens());
            }
            return content.trim();
        } catch (Exception e) {
            // 不输出 API Key；任何网络、超时、鉴权异常都返回 null
            log.warn("DeepSeek 调用失败：{}", e.getMessage());
            return null;
        }
    }

    @Override
    public String answerGameQuestion(String question, String dataContext) {
        String userPrompt = """
                用户问题：
                %s

                【数据库查询结果】
                %s

                请只根据以上数据组织回答。
                """.formatted(question, dataContext);
        return chat(GAME_ANSWER_PROMPT, userPrompt);
    }
}