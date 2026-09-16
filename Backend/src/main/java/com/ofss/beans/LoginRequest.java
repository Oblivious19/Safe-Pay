package com.ofss.beans;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/** Exactly one existing email/mobile identifier and the account password. No PIN. */
public class LoginRequest {
    private String email;
    private String phone;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static LoginRequest fromJson(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid();
        var names = body.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!"email".equals(name) && !"phone".equals(name) && !"password".equals(name)) throw invalid();
        }
        if (body.has("email") == body.has("phone")) throw invalid();
        LoginRequest request = new LoginRequest();
        request.email = body.has("email") ? text(body.get("email")) : null;
        request.phone = body.has("phone") ? text(body.get("phone")) : null;
        request.password = text(body.get("password"));
        request.validate();
        return request;
    }

    public void validate() {
        if ((email == null) == (phone == null) || password == null || password.isBlank()) throw invalid();
        if (email != null && (email.length() > 150 || !email.matches("[^\\s@]+@[^\\s@]+"))) throw invalid();
        if (phone != null && !phone.matches("[0-9]{10}")) throw invalid();
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) throw invalid();
        return node.textValue();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Supply exactly one valid email or 10-digit phone number, and a password");
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
