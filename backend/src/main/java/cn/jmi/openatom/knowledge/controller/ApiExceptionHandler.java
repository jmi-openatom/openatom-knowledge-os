package cn.jmi.openatom.knowledge.controller;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
  ResponseEntity<Map<String, Object>> badRequest(Exception exception) {
    return response(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getFieldErrors().stream()
        .findFirst().map(error -> error.getField() + " " + error.getDefaultMessage())
        .orElse("请求参数不正确");
    return response(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> unknown(Exception exception) {
    return response(HttpStatus.INTERNAL_SERVER_ERROR, exception.getMessage() == null ? "服务器内部错误" : exception.getMessage());
  }

  private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of(
        "timestamp", Instant.now().toString(),
        "status", status.value(),
        "error", status.getReasonPhrase(),
        "message", message));
  }
}
