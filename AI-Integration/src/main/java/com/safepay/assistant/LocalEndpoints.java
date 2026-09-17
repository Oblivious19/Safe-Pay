package com.safepay.assistant;
import java.net.URI;
final class LocalEndpoints {
    static URI base(String value) {
        URI uri=URI.create(value);
        if (!"http".equals(uri.getScheme()) || !java.util.Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost())
            || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
            || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("Configure a loopback HTTP base URL without credentials or path");
        }
        return uri;
    }
}
