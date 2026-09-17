package com.safepay.assistant;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.safepay.assistant.Models.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SafePayClient {
    private final URI base; private final ObjectMapper json;
    public SafePayClient(@Value("${assistant.backend-url}") String base,ObjectMapper json) {
        this.base=LocalEndpoints.base(base);this.json=json;
    }
    public BackendSession login(LoginInput input) {
        if (input==null || input.password()==null || input.password().isBlank() || input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72
            || (input.email()==null && input.phone()==null) || (input.email()!=null && input.phone()!=null)
            || (input.email()!=null && input.email().length()>150) || (input.phone()!=null && input.phone().length()>15)) {
            throw new AppError(400,"Enter your existing admin email or mobile number and password");
        }
        try {
            Map<String,String> body=new HashMap<>();body.put("password",input.password());
            if(input.phone()!=null)body.put("phone",input.phone());else body.put("email",input.email());
            var response=send("/api/auth/login","POST",Map.of("Content-Type","application/json"),json.writeValueAsString(body),10);
            if(response.statusCode()!=200) {
                if(response.statusCode()==401)throw new AppError(401,"Invalid admin credentials");
                if(response.statusCode()==403)throw new AppError(403,"SafePay account is inactive or temporarily locked");
                throw new AppError(502,"SafePay could not complete sign-in");
            }
            Identity identity=json.readValue(response.body(),Identity.class);
            String cookie=response.headers().allValues("set-cookie").stream().map(s->s.split(";",2)[0])
                .filter(s->s.startsWith("JSESSIONID=")).findFirst().orElseThrow(()->new AppError(502,"SafePay did not create a session"));
            BackendSession session=new BackendSession(cookie,identity);
            if(!"ADMIN".equals(identity.role()) || !"ACTIVE".equals(identity.status())) {
                logout(session);throw new AppError(403,"Only an active SafePay administrator can use this assistant");
            }
            pending(session);return session;
        } catch(AppError e){throw e;} catch(Exception e){throw new AppError(502,"SafePay sign-in is unavailable. Start the existing backend and try again.");}
    }
    public List<Pending> pending(BackendSession session) {
        if(session==null)throw new AppError(401,"Sign in with your SafePay administrator account");
        var response=send("/api/admin/transactions/hard-holds","GET",Map.of("Cookie",session.cookie()),null,10);
        if(response.statusCode()==401 || response.statusCode()==403)throw new AppError(response.statusCode(),"Your SafePay admin session expired or access changed. Sign in again.");
        if(response.statusCode()!=200)throw new AppError(502,"SafePay pending requests are unavailable");
        try { return json.readValue(response.body(),json.getTypeFactory().constructCollectionType(List.class,Pending.class)); }
        catch(Exception e){throw new AppError(502,"SafePay returned an unsupported payment response");}
    }
    public Pending pending(BackendSession session,long id) {
        return pending(session).stream().filter(p->Objects.equals(p.transactionId(),id)).findFirst()
            .orElseThrow(()->new AppError(409,"This payment is no longer awaiting approval. Refresh the pending list."));
    }
    public void logout(BackendSession session) {
        if(session==null)return;
        try {
            var response=send("/api/admin/transactions/hard-holds","GET",Map.of("Cookie",session.cookie()),null,10);
            String token=response.headers().firstValue("X-CSRF-TOKEN").orElse("");
            send("/api/auth/logout","POST",Map.of("Cookie",session.cookie(),"X-CSRF-TOKEN",token),"",4);
        }catch(Exception ignored){ /* Local session is invalidated even when upstream is unavailable. */ }
    }
    private LocalHttp.Response send(String path,String method,Map<String,String> headers,String body,int timeout) {
        try{return LocalHttp.send(base.resolve(path),method,headers,body,timeout);}
        catch(Exception e){throw new AppError(502,"SafePay backend is unavailable. Start it and try again.");}
    }
}
