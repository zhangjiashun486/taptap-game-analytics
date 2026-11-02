package org.example.base_server.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.example.base_server.entity.User;

/**
 * 用户表访问接口（MyBatis 注解版）。
 * 方法名/参数通过注解里的 SQL 直接对应，不需要额外 XML。
 */
@Mapper
public interface UserDao {

    /** 按用户名查询（登录用） */
    @Select("SELECT id, username, password_hash AS passwordHash, nickname, role, status, " +
            "created_at AS createdAt, updated_at AS updatedAt " +
            "FROM t_user WHERE username = #{username} LIMIT 1")
    User findByUsername(String username);

    /** 按主键查询（查看个人信息用） */
    @Select("SELECT id, username, password_hash AS passwordHash, nickname, role, status, " +
            "created_at AS createdAt, updated_at AS updatedAt " +
            "FROM t_user WHERE id = #{id} LIMIT 1")
    User findById(Long id);

    /** 新增用户，并把自增主键写回 user.id */
    @Insert("INSERT INTO t_user(username, password_hash, nickname, role, status, created_at, updated_at) " +
            "VALUES(#{username}, #{passwordHash}, #{nickname}, #{role}, #{status}, NOW(), NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);
}
