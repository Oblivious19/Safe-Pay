package com.safepay.assistant;
import static com.safepay.assistant.Models.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Service;

@Service
public class ReviewService {
    private final SafePayClient backend;private final EvidenceRepository evidence;private final OllamaReviewer ai;private final ObjectMapper json;
    private final Semaphore slots=new Semaphore(2);private final Set<Long> inFlight=ConcurrentHashMap.newKeySet();
    public ReviewService(SafePayClient backend,EvidenceRepository evidence,OllamaReviewer ai,ObjectMapper json){this.backend=backend;this.evidence=evidence;this.ai=ai;this.json=json;}
    public Review review(BackendSession session,long id,boolean useAi) {
        if(id<=0)throw new AppError(400,"Choose a valid transaction");
        Pending pending=backend.pending(session,id);
        if(!inFlight.add(id))throw new AppError(429,"This payment is already being reviewed. Please wait.");
        boolean acquired=false;
        try {
            acquired=slots.tryAcquire();if(!acquired)throw new AppError(429,"The assistant is busy. Try again shortly.");
            Evidence snapshot=evidence.read(pending);
            List<Fact> focus=List.of();List<String> checks=List.of();String mode="EVIDENCE_ONLY";
            String message="Verified database evidence. Request AI review to prioritize observations and suggest review checks.";
            if(useAi) {
                try {
                    AiSelection selection=ai.review(snapshot);
                    Map<String,Fact> facts=new HashMap<>();snapshot.facts().forEach(f->facts.put(f.id(),f));
                    focus=selection.focusEvidenceIds().stream().map(facts::get).toList();
                    checks=selection.suggestedChecks().stream().map(OllamaReviewer.CHECKS::get).toList();
                    mode="AI_ASSISTED";message="AI selected these observations and review checks. All displayed figures and evidence text were calculated by the backend. This is not a fraud verdict.";
                }catch(AppError failure) { message=failure.getMessage(); }
            }
            // Revoke expired/admin-changed sessions and suppress results for payments settled during inference.
            backend.pending(session,id);evidence.requireUnchanged(id,snapshot.version());
            String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(snapshot).getBytes(StandardCharsets.UTF_8)));
            return new Review(UUID.randomUUID().toString(),snapshot,mode,mode.equals("AI_ASSISTED")?ai.model():null,message,focus,checks,Instant.now(),digest);
        }catch(AppError e){throw e;}catch(Exception e){throw new AppError(503,"Unable to prepare this review");}
        finally{if(acquired)slots.release();inFlight.remove(id);}
    }
}
