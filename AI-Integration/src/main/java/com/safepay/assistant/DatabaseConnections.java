package com.safepay.assistant;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DatabaseConnections {
    private final String propertiesPath; private final Environment env;
    public DatabaseConnections(@Value("${assistant.database-properties}") String propertiesPath,Environment env){this.propertiesPath=propertiesPath;this.env=env;}
    public Connection open() throws SQLException {
        Properties props=new Properties();
        try(Reader reader=Files.newBufferedReader(Path.of(propertiesPath),StandardCharsets.UTF_8)){props.load(reader);}
        catch(Exception e){throw new AppError(503,"Cannot read SafePay database configuration. Check SAFEPAY_DATABASE_PROPERTIES.");}
        String url=setting(props,"spring.datasource.url","ASSISTANT_DB_URL");
        String user=setting(props,"spring.datasource.username","ASSISTANT_DB_USERNAME");
        String password=setting(props,"spring.datasource.password","ASSISTANT_DB_PASSWORD");
        if(!url.startsWith("jdbc:oracle:") && !url.startsWith("jdbc:h2:"))throw new AppError(503,"Unsupported assistant database configuration");
        Properties auth=new Properties();auth.put("user",user);auth.put("password",password);
        auth.put("oracle.net.CONNECT_TIMEOUT","5000");auth.put("oracle.jdbc.ReadTimeout","10000");
        Connection connection=DriverManager.getConnection(url,auth);
        try {
            connection.setReadOnly(true); connection.setAutoCommit(false);
            if(connection.getMetaData().getDatabaseProductName().toLowerCase().contains("oracle")) {
                // Oracle enforces no writes and a consistent database snapshot for all review queries.
                try(Statement statement=connection.createStatement()){statement.execute("SET TRANSACTION READ ONLY");}
            }
            return connection;
        }catch(SQLException e){connection.close();throw e;}
    }
    private String setting(Properties props,String key,String override){
        String value=env.getProperty(override,props.getProperty(key,""));
        return key.endsWith("password") ? env.resolveRequiredPlaceholders(value) : env.resolveRequiredPlaceholders(value).trim();
    }
}
