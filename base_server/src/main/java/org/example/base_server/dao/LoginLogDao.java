package org.example.base_server.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.example.base_server.entity.LoginLog;

/** 登录日志写入 */
@Mapper
public interface LoginLogDao {

    @Insert("INSERT INTO t_login_log(user_id, username, role, login_ip, user_agent, success, message, login_time) " +
            "VALUES(#{userId}, #{username}, #{role}, #{loginIp}, #{userAgent}, #{success}, #{message}, NOW())")
    int insert(LoginLog log);
}
