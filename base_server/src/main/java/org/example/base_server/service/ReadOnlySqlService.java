package org.example.base_server.service;

import org.example.base_server.vo.SqlQueryResult;

/** 只读 SQL 执行服务：外部必须先经过安全校验，再连接只读数据库账号。 */
public interface ReadOnlySqlService {

    boolean isAvailable();

    SqlQueryResult execute(String sql);
}