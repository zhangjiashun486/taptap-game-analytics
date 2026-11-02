package org.example.base_server.service;

import org.example.base_server.dto.BehaviorRequest;
import org.example.base_server.vo.BehaviorItem;

import java.util.List;
import java.util.Map;

/** 用户行为业务接口 */
public interface BehaviorService {

    /** 记录行为：校验游戏存在、行为类型合法、评分范围 1~10 */
    Map<String, Object> record(Long userId, BehaviorRequest request);

    /** 查询我的行为记录 */
    List<BehaviorItem> listMine(Long userId);
}
