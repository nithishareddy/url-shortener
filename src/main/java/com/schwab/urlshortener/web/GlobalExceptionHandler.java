package com.schwab.urlshortener.web;

import com.schwab.urlshortener.exception.AliasConflictException;
import com.schwab.urlshortener.exception.InvalidAliasException;
import com.schwab.urlshortener.exception.InvalidUrlException;
import com.schwab.urlshortener.exception.ShortUrlGoneException;
import com.schwab.urlshortener.exception.ShortUrlNotFoundException;
import com.schwab.urlshortener.exception.UnsafeUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ShortUrlNotFoundException.class)
  public ProblemDetail handleNotFound(ShortUrlNotFoundException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
  }

  @ExceptionHandler(ShortUrlGoneException.class)
  public ProblemDetail handleGone(ShortUrlGoneException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.GONE, e.getMessage());
  }

  @ExceptionHandler({
    InvalidUrlException.class,
    UnsafeUrlException.class,
    InvalidAliasException.class
  })
  public ProblemDetail handleBadRequest(RuntimeException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }

  @ExceptionHandler(AliasConflictException.class)
  public ProblemDetail handleConflict(AliasConflictException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidation(MethodArgumentNotValidException e) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception e) {
    log.error("Unhandled exception", e);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
  }
}
