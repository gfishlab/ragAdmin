package com.ragadmin.server.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    @Test
    void shouldDeclareDedicatedHandlerForAsyncRequestNotUsableException() {
        Method handlerMethod = Arrays.stream(GlobalExceptionHandler.class.getDeclaredMethods())
                .filter(this::isAsyncRequestNotUsableHandler)
                .findFirst()
                .orElse(null);

        assertNotNull(handlerMethod);
        assertEquals(void.class, handlerMethod.getReturnType());
    }

    private boolean isAsyncRequestNotUsableHandler(Method method) {
        ExceptionHandler exceptionHandler = method.getAnnotation(ExceptionHandler.class);
        if (exceptionHandler == null) {
            return false;
        }
        return Arrays.asList(exceptionHandler.value()).contains(AsyncRequestNotUsableException.class);
    }
}
