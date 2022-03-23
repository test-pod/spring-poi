package com.shang.poi.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Created by shangwei2009@hotmail.com on 2020/11/16 11:11
 */
@Data
public class R<T> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.CHINA);

    private Integer code;

    private String message;

    private T data;

    private String time;

    @JsonCreator
    private R() {
        /*
         * 为了服务消费者能顺利将R类逆序列化，必须保证拥有默认构造函数。
         * 如果访问权限为public则无需@JsonCreator。
         * 如果访问权限为private则必须加上@JsonCreator。
         */
    }

    private R(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.time = LocalDateTime.now().format(FORMATTER);
    }

    public static <T> R<T> of(Code code, String message, T data) {
        return new R<>(code.getCode(), message, data);
    }

    public static R<Void> of(Code code, Throwable throwable) {
        return new R<>(code.getCode(), throwable.getLocalizedMessage(), null);
    }

    public enum Code {

        /**
         * 请求成功
         */
        OK(20000),
        /**
         * 请求失败
         */
        NOT_OK(20002),
        /**
         * 熔断请求
         */
        BREAKING(20004),
        /**
         * 认证失败
         */
        UNAUTHORIZED(40001),
        /**
         * Forbidden
         */
        FORBIDDEN(40003),
        /**
         * 404
         */
        NOT_FOUND(40004),
        /**
         * 405，Method not allowed
         */
        METHOD_NOT_ALLOWED(40005),
        /**
         * 内部错误
         */
        INTERNAL_ERROR(50000),
        /**
         * 非法令牌
         */
        ILLEGAL_TOKEN(50008),
        /**
         * 令牌过期
         */
        TOKEN_EXPIRED(50014);


        private final Integer code;

        Code(Integer code) {
            this.code = code;
        }

        public Integer getCode() {
            return code;
        }

    }
}
