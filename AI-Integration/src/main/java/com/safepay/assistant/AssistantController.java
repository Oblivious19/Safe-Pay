package com.safepay.assistant;
import static com.safepay.assistant.Models.*;
import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AssistantController {
    static final String SESSION="safepay.assistant.backendSession";
    private final SafePayClient backend;private final ReviewService reviews;private final OllamaReviewer ai;private final String ui;
    public AssistantController(SafePayClient backend,ReviewService reviews,OllamaReviewer ai,@Value("${assistant.ui-url}") String ui){this.backend=backend;this.reviews=reviews;this.ai=ai;this.ui=ui;}
    @GetMapping("/session")
    public Map<String,Object> session(HttpServletRequest request,CsrfToken csrf,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");var current=request.getSession(false);
        BackendSession saved=current==null?null:(BackendSession)current.getAttribute(SESSION);
        if(saved!=null)backend.pending(saved);
        var result=new HashMap<String,Object>();result.put("csrf",csrf.getToken());result.put("signedIn",saved!=null);result.put("safePayAdminUrl",ui);
        if(saved!=null)result.put("identity",saved.identity());return result;
    }
    @PostMapping("/login") public Identity login(@RequestBody LoginInput input,HttpServletRequest request){
        BackendSession logged=backend.login(input);var old=request.getSession(false);
        if(old!=null){BackendSession previous=(BackendSession)old.getAttribute(SESSION);old.invalidate();backend.logout(previous);}
        request.getSession(true).setAttribute(SESSION,logged);return logged.identity();
    }
    @PostMapping("/logout") public Map<String,String> logout(HttpServletRequest request){
        var session=request.getSession(false);if(session!=null){BackendSession saved=(BackendSession)session.getAttribute(SESSION);session.invalidate();backend.logout(saved);}
        return Map.of("message","Signed out of the assistant");
    }
    @GetMapping("/pending") public List<Pending> pending(HttpServletRequest request){return backend.pending(require(request));}
    @GetMapping("/model") public Map<String,Object> model(HttpServletRequest request){backend.pending(require(request));return ai.status();}
    @GetMapping("/payments/{id}/evidence") public Review evidence(@PathVariable long id,HttpServletRequest request){return reviews.review(require(request),id,false);}
    @PostMapping("/payments/{id}/review") public Review review(@PathVariable long id,HttpServletRequest request){return reviews.review(require(request),id,true);}
    private BackendSession require(HttpServletRequest request){
        var session=request.getSession(false);BackendSession saved=session==null?null:(BackendSession)session.getAttribute(SESSION);
        if(saved==null)throw new AppError(401,"Sign in with your existing SafePay admin account");return saved;
    }
}
