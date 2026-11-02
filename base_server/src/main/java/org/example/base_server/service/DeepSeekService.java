package org.example.base_server.service;

import java.util.Map;

/** DeepSeek 大模型服务；未启用或调用失败时返回 null，由上层回退。 */
public interface DeepSeekService {

    /** 当前是否具备调用条件：开关开启且 Key 已配置。 */
    boolean isEnabled();

    /** 返回不包含密钥的状态信息，供健康检查页面展示。 */
    Map<String, Object> status();

    /** 通用对话入口：由上层提供 system/user 提示词。 */
    String chat(String systemPrompt, String userPrompt);

    /**
     * 基于数据库真实查询结果组织自然语言回答。
     *
     * @param question    用户原始问题
     * @param dataContext 规则问答先从 MySQL 查到的数据摘要
     * @return 大模型回答；失败返回 null
     */
    String answerGameQuestion(String question, String dataContext);
}