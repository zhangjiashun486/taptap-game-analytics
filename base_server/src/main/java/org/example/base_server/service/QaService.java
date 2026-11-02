package org.example.base_server.service;

import org.example.base_server.vo.QaAnswer;

/** 数据问答业务接口 */
public interface QaService {

    /** 回答问题：解析意图 → 查询数据 → 组织答案 + 写问答日志 */
    QaAnswer ask(Long userId, String question);
}
