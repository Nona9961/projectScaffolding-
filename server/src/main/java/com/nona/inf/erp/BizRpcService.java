package com.nona.inf.erp;

import com.nona.api.HttpResponse;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * http interface feature
 */
@HttpExchange("/api/erp/v1/")
public interface BizRpcService {

    @GetExchange("/list")
    HttpResponse<?> list();
}