import java.nio.file.*;
import java.sql.*;
import java.util.Properties;

/** Java 17 source launcher. Defaults to a read-only schema check; use "apply" explicitly for DDL. */
class ApplyReceiverMigration {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("Usage: ApplyReceiverMigration.java <backend-directory> [apply]");
        Path root=Path.of(args[0]).toAbsolutePath().normalize();
        Properties config=new Properties();
        try(var in=Files.newInputStream(root.resolve("src/main/resources/application.properties"))){config.load(in);}
        String url=setting(config,"SPRING_DATASOURCE_URL","spring.datasource.url");
        if(url==null || !url.startsWith("jdbc:oracle:thin:@localhost:"))
            throw new IllegalStateException("Only the configured local Oracle database is supported. Review the SQL manually for other environments.");
        Properties connection=new Properties();
        connection.setProperty("user",setting(config,"SPRING_DATASOURCE_USERNAME","spring.datasource.username"));
        connection.setProperty("password",setting(config,"SPRING_DATASOURCE_PASSWORD","spring.datasource.password"));
        connection.setProperty("oracle.net.CONNECT_TIMEOUT","5000");
        connection.setProperty("oracle.jdbc.ReadTimeout","15000");
        try(Connection db=DriverManager.getConnection(url,connection);Statement statement=db.createStatement()) {
            System.out.println("Database: "+db.getMetaData().getDatabaseProductName()+"; schema: "+db.getMetaData().getUserName());
            try(ResultSet rows=statement.executeQuery("select count(*) from user_tables where table_name in ('ACCOUNT','BENEFICIARIES','TRANSACTION_DB')")) {
                rows.next();if(rows.getInt(1)!=3)throw new IllegalStateException("Expected SafePay tables were not found; no changes made.");
            }
            if(args.length>1 && args[1].equals("apply")) {
                String sql=Files.readString(root.resolve("db/24-internal-transfer-recipient.sql")).replaceFirst("/\\s*$","");
                statement.execute(sql);
                System.out.println("Recipient column, foreign key and index are installed. No account balances or historical payments were changed.");
            }
            try(ResultSet rows=statement.executeQuery("select count(*) from user_tab_columns where table_name='TRANSACTION_DB' and column_name='TO_ACCOUNT_ID'")) {
                rows.next();System.out.println("Recipient column present: "+(rows.getInt(1)==1));
            }
        }
    }
    private static String setting(Properties config,String env,String property) {
        String value=System.getenv(env);return value==null?config.getProperty(property):value;
    }
}
