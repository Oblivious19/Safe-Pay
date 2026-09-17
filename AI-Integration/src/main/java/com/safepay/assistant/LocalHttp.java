package com.safepay.assistant;
import java.net.*;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
/** Bounded blocking HTTP avoids the Windows JDK selector's Unix-domain wake-up pipe. */
final class LocalHttp {
    record Response(int statusCode,String body,HttpHeaders headers){}
    static Response send(URI url,String method,Map<String,String> headers,String body,int seconds)throws IOException{
        HttpURLConnection connection=(HttpURLConnection)url.toURL().openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(3000);connection.setReadTimeout(seconds*1000);connection.setRequestMethod(method);
        headers.forEach(connection::setRequestProperty);
        if(headers.keySet().stream().noneMatch("Accept"::equalsIgnoreCase))connection.setRequestProperty("Accept","application/json");
        try {
            if(body!=null){byte[] bytes=body.getBytes(StandardCharsets.UTF_8);connection.setDoOutput(true);connection.setFixedLengthStreamingMode(bytes.length);
                try(OutputStream output=connection.getOutputStream()){output.write(bytes);}}
            int status=connection.getResponseCode();Map<String,List<String>> responseHeaders=new HashMap<>();
            connection.getHeaderFields().forEach((name,values)->{if(name!=null)responseHeaders.put(name,values);});
            InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();String text="";
            if(stream!=null)try(stream){byte[] bytes=stream.readNBytes(512001);if(bytes.length>512000)throw new IOException("Response limit exceeded");text=new String(bytes,StandardCharsets.UTF_8);}
            return new Response(status,text,HttpHeaders.of(responseHeaders,(name,value)->true));
        }finally{connection.disconnect();}
    }
}
