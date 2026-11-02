package org.example.base_server.service;

import org.example.base_server.vo.QaAnswer;

/** FALLBACK 问题的增强代理：只读 SQL 查询或通用知识补充。 */
public interface QaAgentService {

    QaAnswer answer(Long userId, String question);
}