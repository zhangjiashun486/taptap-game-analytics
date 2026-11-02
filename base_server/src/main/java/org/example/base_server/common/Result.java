package org.example.base_server.common;

/**
 * 统一响应结构：{ code, message, data }
 * 前端拿到后只需判断 code == 200。
 */
public class Result<T> {

    private int code;          // 状态码：200 成功，其它为失败
    private String message;    // 提示信息
    private T data;            // 业务数据

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功（带提示） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }

    /** 失败 */
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
