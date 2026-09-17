package com.safepay.assistant;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.function.Function;
/** Local blocking HTTP fixture: no external network, credentials or model downloads. */
final class MiniServer implements AutoCloseable {
    record Request(String method,String path,Map<String,String> headers,String body){}
    record Response(int status,Map<String,String> headers,String body){}
    final ServerSocket socket;final Thread worker;volatile boolean closed=false;
    MiniServer(Function<Request,Response> handler)throws IOException{
        socket=new ServerSocket(0,20,InetAddress.getByName("127.0.0.1"));
        worker=new Thread(()->{while(!closed){try(Socket client=socket.accept()){
            client.setSoTimeout(10000);InputStream in=client.getInputStream();String[] start=line(in).split(" ");Map<String,String> headers=new HashMap<>();String next;
            while(!(next=line(in)).isEmpty()){int colon=next.indexOf(':');headers.put(next.substring(0,colon).toLowerCase(),next.substring(colon+1).trim());}
            int length=Integer.parseInt(headers.getOrDefault("content-length","0"));
            Response response=handler.apply(new Request(start[0],start[1],headers,new String(in.readNBytes(length),StandardCharsets.UTF_8)));
            byte[] body=response.body().getBytes(StandardCharsets.UTF_8);StringBuilder head=new StringBuilder("HTTP/1.1 "+response.status()+" Result\r\nContent-Type: application/json\r\nConnection: close\r\nContent-Length: "+body.length+"\r\n");
            response.headers().forEach((k,v)->head.append(k).append(": ").append(v).append("\r\n"));head.append("\r\n");
            client.getOutputStream().write(head.toString().getBytes(StandardCharsets.UTF_8));client.getOutputStream().write(body);client.getOutputStream().flush();
        }catch(Exception e){if(!closed)throw new RuntimeException(e);}}},"assistant-http-test");worker.setDaemon(true);worker.start();
    }
    String url(){return "http://127.0.0.1:"+socket.getLocalPort();}
    private static String line(InputStream stream)throws IOException{ByteArrayOutputStream bytes=new ByteArrayOutputStream();int b;while((b=stream.read())!=-1 && b!='\n'){if(b!='\r')bytes.write(b);}return bytes.toString(StandardCharsets.UTF_8);}
    public void close()throws Exception{closed=true;socket.close();worker.join(2000);}
}
