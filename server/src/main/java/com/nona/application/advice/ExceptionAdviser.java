package com.nona.application.advice;

import com.nona.api.HttpResponse;
import com.nona.exceptions.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import com.nona.annotation.ScaffoldGenerated;

@RestControllerAdvice
@Slf4j
@ScaffoldGenerated
public class ExceptionAdviser {

    @ExceptionHandler(BusinessException.class)
    public HttpResponse<?> handleBusinessException(BusinessException e) {
        return HttpResponse.fail(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public HttpResponse<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        log.error("illegal args :{}", errors);
        return HttpResponse.fail(errors);
    }

    @ExceptionHandler(BindException.class)
    public HttpResponse<?> handleBindException(BindException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        log.error("illegal args :{}", errors);
        return HttpResponse.fail(errors);
    }

    @ExceptionHandler(RuntimeException.class)
    public HttpResponse<?> handleRuntimeException(RuntimeException e) {
        log.error("unhandled exception : {}", e.getMessage());
        log.error("stack is ", e);
        return HttpResponse.fail("Severe internal error. Please retry later.");
    }
}
