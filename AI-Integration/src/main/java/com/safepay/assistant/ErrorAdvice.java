package com.safepay.assistant;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice
public class ErrorAdvice {
    @ExceptionHandler(AppError.class) ResponseEntity<Map<String,String>> known(AppError error,HttpServletRequest request){
        if(error.status()==401 || error.status()==403){var s=request.getSession(false);if(s!=null)s.invalidate();}
        return ResponseEntity.status(error.status()).header("Cache-Control","no-store").body(Map.of("message",error.getMessage()));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String,String>> badInput(){return ResponseEntity.badRequest().body(Map.of("message","Invalid request fields"));}
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<Map<String,String>> notFound(){return ResponseEntity.status(404).body(Map.of("message","This assistant endpoint does not exist"));}
    @ExceptionHandler(Exception.class) ResponseEntity<Map<String,String>> unexpected(){return ResponseEntity.status(500).body(Map.of("message","The assistant could not complete this request"));}
}
