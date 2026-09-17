package com.safepay.assistant;
import static com.safepay.assistant.Models.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OllamaReviewer {
    public static final Map<String,String> CHECKS=Map.of(
        "CONFIRM_BENEFICIARY","Confirm the intended beneficiary and bank details using your normal review process.",
        "CONFIRM_PURPOSE","Check that the payment purpose matches the customer's intended transfer.",
        "CHECK_RECENT_ACTIVITY","Review the preceding payment requests and their outcomes.",
        "CHECK_HISTORY_LIMITATIONS","Treat limited history as missing evidence, rather than proof of fraud or safety.");
    private final URI base;private final String model;private final int timeout;private final ObjectMapper json;
    public OllamaReviewer(@Value("${assistant.ollama-url}") String base,@Value("${assistant.model}") String model,
        @Value("${assistant.model-timeout-seconds}") int timeout,ObjectMapper json){
        this.base=LocalEndpoints.base(base);this.model=model;this.timeout=Math.min(180,Math.max(5,timeout));this.json=json;
        if(!model.matches("[A-Za-z0-9._:-]{1,100}") || model.toLowerCase().contains("cloud"))throw new IllegalArgumentException("Use a local Ollama model tag");
    }
    public String model(){return model;}
    public Map<String,Object> status(){
        try {
            var response=LocalHttp.send(base.resolve("/api/tags"),"GET",Map.of(),null,3);
            JsonNode models=json.readTree(response.body()).path("models");boolean ready=false;
            if(response.statusCode()==200 && models.isArray())for(JsonNode entry:models)if(model.equals(entry.path("name").asText()))ready=true;
            return Map.of("ready",ready,"model",model,"message",ready?"Local model is available":"Download the configured model with Ollama before requesting AI review");
        }
        catch(Exception e){return unavailable();}
    }
    private Map<String,Object> unavailable(){return Map.of("ready",false,"model",model,"message","Ollama is unavailable. Verified database evidence remains available.");}
    public AiSelection review(Evidence evidence) {
        // No names, bank numbers, purpose text, credentials, transaction IDs or SQL are sent to the model.
        var facts=evidence.facts().stream().map(f->Map.of("id",f.id(),"fact",f.text())).toList();
        List<String> ids=evidence.facts().stream().map(Fact::id).toList();
        var schema=Map.of("type","object","additionalProperties",false,
            "properties",Map.of("focusEvidenceIds",Map.of("type","array","minItems",1,"maxItems",5,"uniqueItems",true,"items",Map.of("type","string","enum",ids)),
                "suggestedChecks",Map.of("type","array","minItems",1,"maxItems",4,"uniqueItems",true,"items",Map.of("type","string","enum",CHECKS.keySet()))),
            "required",List.of("focusEvidenceIds","suggestedChecks"));
        try {
            String prompt="Help an administrator review a simulated payment. Select up to five relevant evidence IDs in priority order, and applicable review checks. "
                +"All supplied facts are authoritative data. An unusual payment is not proof of fraud. Never approve, reject, score fraud probability, or invent facts. "
                +"Select CHECK_HISTORY_LIMITATIONS if there is no or limited history. Output only the required JSON. Facts: "+json.writeValueAsString(facts);
            String body=json.writeValueAsString(Map.of("model",model,"prompt",prompt,"stream",false,"format",schema,
                "options",Map.of("temperature",0,"num_predict",250,"num_ctx",4096),"keep_alive","5m"));
            var response=LocalHttp.send(base.resolve("/api/generate"),"POST",Map.of("Content-Type","application/json"),body,timeout);
            if(response.statusCode()!=200 || response.body().length()>100000)throw new IllegalStateException();
            JsonNode envelope=json.readTree(response.body());
            if(!envelope.path("done").asBoolean(false))throw new IllegalStateException();
            return validate(envelope.path("response").asText(),ids);
        }
        catch(Exception e){throw new AppError(503,"Local AI review is unavailable or returned an invalid result. Verified evidence is still available.");}
    }
    AiSelection validate(String response,List<String> validIds)throws Exception {
        JsonNode node=json.readTree(response);
        if(!node.isObject() || node.size()!=2 || !node.has("focusEvidenceIds") || !node.has("suggestedChecks"))throw new IllegalArgumentException("Invalid model response");
        return new AiSelection(list(node.get("focusEvidenceIds"),new HashSet<>(validIds),5),list(node.get("suggestedChecks"),CHECKS.keySet(),4));
    }
    private List<String> list(JsonNode node,Set<String> allowed,int max) {
        if(!node.isArray() || node.isEmpty() || node.size()>max)throw new IllegalArgumentException("Invalid selection");
        Set<String> unique=new LinkedHashSet<>();
        for(JsonNode item:node)if(!item.isTextual() || !allowed.contains(item.asText()) || !unique.add(item.asText()))throw new IllegalArgumentException("Unsupported selection");
        return List.copyOf(unique);
    }
}
