import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

// Standalone evidence utility. Does not load Spring, backend settings or environment secrets.
public final class SnapshotRunner {
  private static Map<String,String> credentials(String text) {
    Map<String,String> fields = new LinkedHashMap<>();
    for (String line : text.replace("\uFEFF","").split("\\R")) {
      if (line.isBlank() || line.startsWith("#")) continue;
      int split = line.indexOf('=');
      if (split < 1) throw new IllegalArgumentException("Use key=value lines in the credentials file.");
      String key = line.substring(0,split).trim();
      if (!Set.of("jdbcUrl","username","password").contains(key) || fields.putIfAbsent(key,line.substring(split+1)) != null)
        throw new IllegalArgumentException("Unexpected or duplicate credentials field.");
    }
    if (!fields.keySet().containsAll(Set.of("jdbcUrl","username","password"))) throw new IllegalArgumentException("Missing connection fields.");
    if (!"SAFEPAY_OWNER".equals(fields.get("username"))) throw new IllegalArgumentException("Only the designated SAFEPAY_OWNER login is accepted.");
    if (!fields.get("jdbcUrl").matches("jdbc:oracle:thin:@//(?:localhost|127\\.0\\.0\\.1):[0-9]{1,5}/[A-Za-z0-9_.-]+"))
      throw new IllegalArgumentException("Use the existing local Oracle service URL: jdbc:oracle:thin:@//localhost:port/service.");
    if (fields.get("password").isBlank() || fields.get("password").startsWith("REPLACE_"))
      throw new IllegalArgumentException("The credentials file still needs the existing owner password.");
    return fields;
  }
  private static String block(Path evidence) throws Exception {
    String source = Files.readString(evidence.resolve("capture-safe-baseline.sql"),StandardCharsets.UTF_8).replace("\r\n","\n");
    int start=source.indexOf("\nDECLARE\n"), end=source.lastIndexOf("\n/\nROLLBACK;");
    if(start<0 || end<start || !source.contains("SET TRANSACTION READ ONLY;") || !source.contains("SAFEPAY_SNAPSHOT_V2|"))
      throw new IllegalArgumentException("The reviewed V2 snapshot script is missing or has an unexpected format.");
    String value=source.substring(start+1,end);
    if(java.util.regex.Pattern.compile("\\b(INSERT|UPDATE|DELETE|MERGE|COMMIT|GRANT|REVOKE|ALTER|DROP|TRUNCATE|CREATE)\\b",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value).find())
      throw new IllegalArgumentException("The snapshot block must remain read-only.");
    return value;
  }
  private static void selfTest(Path evidence) throws Exception {
    block(evidence);
    var sample=credentials("jdbcUrl=jdbc:oracle:thin:@//localhost:1521/FREEPDB1\nusername=SAFEPAY_OWNER\npassword=a=b\\c#literal");
    if(!sample.get("password").equals("a=b\\c#literal"))throw new IllegalStateException("Literal password parsing failed.");
    boolean rejected=false;
    try { credentials("jdbcUrl=jdbc:oracle:thin:@//remote-host:1521/FREEPDB1\nusername=SAFEPAY_OWNER\npassword=test"); }
    catch(IllegalArgumentException expected){rejected=true;}
    if(!rejected)throw new IllegalStateException("Remote connection guard failed.");
    System.out.println("PASS: runner compiled; read-only SQL shape, literal credential parsing and local-host guard checked. No credentials read and no database connection opened.");
  }
  public static void main(String[] args) {
    try {
      Path evidence=Path.of("integration-evidence").toAbsolutePath().normalize();
      if(args.length==1 && args[0].equals("--self-test")) { selfTest(evidence); return; }
      if(args.length!=2)throw new IllegalArgumentException("Usage from SafePay-New: SnapshotRunner credentials-file-or--stdin baseline-before-v2.txt (or baseline-after-v2.txt).");
      if(!Set.of("baseline-before-v2.txt","baseline-after-v2.txt").contains(args[1]))throw new IllegalArgumentException("Use an approved before/after snapshot filename.");
      Path output=evidence.resolve(args[1]);
      if(Files.exists(output))throw new IllegalArgumentException("Snapshot already exists; it will not be overwritten.");
      String sql=block(evidence);
      String credentialText;
      if(args[0].equals("--stdin")) {
        byte[] input=System.in.readNBytes(16385);
        if(input.length>16384)throw new IllegalArgumentException("Connection input is too large.");
        credentialText=new String(input,StandardCharsets.UTF_8);Arrays.fill(input,(byte)0);
      } else {
        Path credentialPath=Path.of(args[0]).toAbsolutePath().normalize().toRealPath();
        Path privateRoot=evidence.resolve("private").toRealPath();
        if(!credentialPath.startsWith(privateRoot))throw new IllegalArgumentException("Keep the authorized connection file in integration-evidence/private.");
        credentialText=Files.readString(credentialPath,StandardCharsets.UTF_8);
      }
      Map<String,String> fields=credentials(credentialText);credentialText=null;
      Properties properties=new Properties();
      properties.setProperty("user",fields.get("username"));properties.setProperty("password",fields.get("password"));
      properties.setProperty("oracle.net.CONNECT_TIMEOUT","10000");properties.setProperty("oracle.jdbc.ReadTimeout","60000");
      DriverManager.setLoginTimeout(10);
      StringBuilder capture=new StringBuilder();
      try(Connection connection=DriverManager.getConnection(fields.get("jdbcUrl"),properties)) {
        fields.clear();properties.clear();connection.setAutoCommit(false);
        try {
          try(Statement statement=connection.createStatement()) {
            statement.setQueryTimeout(60);
            statement.execute("SET TRANSACTION READ ONLY");
            try(ResultSet result=statement.executeQuery("SELECT USER FROM DUAL")) {
              if(!result.next() || !"SAFEPAY_OWNER".equals(result.getString(1)))throw new IllegalStateException("Unexpected connected user.");
            }
            statement.execute("BEGIN DBMS_OUTPUT.ENABLE(NULL); END;");
            statement.execute(sql);
          }
          try(CallableStatement lines=connection.prepareCall("BEGIN DBMS_OUTPUT.GET_LINE(?,?); END;")) {
            lines.setQueryTimeout(60);lines.registerOutParameter(1,Types.VARCHAR);lines.registerOutParameter(2,Types.INTEGER);
            for(int count=0;count<1000000;count++) {
              lines.execute();if(lines.getInt(2)!=0)break;
              String line=lines.getString(1);capture.append(line==null?"":line).append('\n');
              if(capture.length()>100000000)throw new IllegalStateException("Snapshot exceeds the expected demo size; stopping.");
            }
          }
        } finally {connection.rollback();}
      } finally {fields.clear();properties.clear();}
      if(!capture.toString().startsWith("SAFEPAY_SNAPSHOT_V2|") || !capture.toString().endsWith("SAFEPAY_SNAPSHOT_END\n"))
        throw new IllegalStateException("Incomplete snapshot; no output was saved.");
      Files.writeString(output,capture,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
      System.out.println("Read-only snapshot saved: "+args[1]+". Transaction rolled back. Run compare-snapshots.cjs --validate before accepting it.");
    } catch(SQLException error) {
      System.err.println("Oracle snapshot failed (error code "+error.getErrorCode()+"). No baseline accepted; connection transaction is rolled back/closed. Credentials are not printed.");
      System.exit(1);
    } catch(Exception error) {
      System.err.println(error instanceof IllegalArgumentException || error instanceof IllegalStateException ? error.getMessage() : "Snapshot could not complete; no baseline accepted. Check the designated file path and local runtime.");
      System.exit(1);
    }
  }
}
