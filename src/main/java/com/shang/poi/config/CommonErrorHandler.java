package com.shang.poi.config;

import com.shang.poi.model.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Path;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 处理所有异常
 * <p>
 * 要处理更多跟参数相关的异常，请查阅{@link DefaultHandlerExceptionResolver#doResolveException(HttpServletRequest, HttpServletResponse, Object, Exception)}
 * <p>
 * Created by shangwei2009@hotmail.com on 2020/11/16 13:36
 */
@RestController
@RestControllerAdvice
@Slf4j
public class CommonErrorHandler implements ErrorController {

    @Override
    public String getErrorPath() {
        /*
         * https://www.baeldung.com/spring-boot-custom-error-page
         * we're returning null as it is anyway going to be ignored
         * */
        return null;
    }

    /**
     * 仅用于处理404异常，必须指定charset=utf-8，status=200
     *
     * @return {@link R}
     */
    @ResponseStatus(HttpStatus.OK)
    @RequestMapping(value = "/error")
    public R<Void> error() {
        log.error("找不到资源");
        return R.of(R.Code.NOT_FOUND, "找不到资源", null);
    }

    /**
     * 处理除404之外的异常（如果这些异常没有被其他处理器处理的话）
     *
     * @param e {@link Exception}
     * @return {@link R}
     */
    @ExceptionHandler
    public Object handleException(Exception e, HttpServletRequest request) {
        log.info("[{}]{}", request.getMethod(), request.getRequestURI());
        log.error(e.getLocalizedMessage(), e);
        if (request.isAsyncStarted()) {
            // 处理SSE异常
            request.getAsyncContext().complete();
            return null;
        }
        return R.of(R.Code.INTERNAL_ERROR, e.getLocalizedMessage(), null);
    }

    /**
     * 参数类型异常（PS: 缺少参数异常是{@link MissingServletRequestParameterException}，由于结果基本可以直接用，就交给{@link Exception}去处理）
     *
     * @param e {@link MethodArgumentTypeMismatchException}
     * @return {@link R}
     */
    @ExceptionHandler
    public R<Map<String, String>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        Map<String, String> result = Collections.singletonMap(e.getName(), String.format("需要类型%s", e.getRequiredType() != null ? e.getRequiredType().getCanonicalName() : "java.lang.String"));
        return R.of(R.Code.NOT_OK, "参数错误", result);
    }

    /**
     * 处理Query Params参数异常
     *
     * @param e {@link ConstraintViolationException}
     * @return {@link R}
     */
    @ExceptionHandler
    public R<Map<String, List<String>>> handleConstraintViolationException(ConstraintViolationException e) {
        final Map<String, List<String>> result = e.getConstraintViolations().stream().collect(Collectors.groupingBy(constraintViolation -> {
            Path path = constraintViolation.getPropertyPath();
            Iterator<Path.Node> iterator = path.iterator();
            String key = "";
            while (iterator.hasNext()) {
                key = iterator.next().getName();
            }
            return key;
        }, Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())));
        return R.of(R.Code.NOT_OK, "参数错误", result);
    }

    /**
     * 处理Request Payload参数异常
     *
     * @param e {@link MethodArgumentNotValidException}
     * @return {@link R}
     */
    @ExceptionHandler
    public R<Map<String, List<String>>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        final Map<String, List<String>> result = e.getBindingResult().getFieldErrors().stream().collect(Collectors.groupingBy(FieldError::getField, Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));
        return R.of(R.Code.NOT_OK, "参数错误", result);
    }

    /**
     * 处理Form Data参数异常
     *
     * @param e {@link BindException}
     * @return {@link R}
     */
    @ExceptionHandler
    public R<Map<String, List<String>>> handleBindException(BindException e) {
        final Map<String, List<String>> result = e.getBindingResult().getFieldErrors().stream().collect(Collectors.groupingBy(FieldError::getField, Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));
        return R.of(R.Code.NOT_OK, "参数错误", result);
    }

}
