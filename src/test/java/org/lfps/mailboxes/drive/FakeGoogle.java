package org.lfps.mailboxes.drive;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.lfps.mailboxes.util.Json;

/**
 * A stand-in for Google's sign-in and Drive servers, running on this
 * computer, so tests can sign in and upload without a real Google account.
 * {@link #install()} points the app at it, and {@link #browser()} plays the
 * part of the user signing in and clicking Allow.
 */
public final class FakeGoogle {

  /** One file in the fake Drive. */
  public static final class File {
    public final String id;
    public final String name;
    public final String parent;
    public final boolean folder;
    public final byte[] content;

    File(String id, String name, String parent, boolean folder, byte[] content) {
      this.id = id;
      this.name = name;
      this.parent = parent;
      this.folder = folder;
      this.content = content;
    }
  }

  /** The files in the fake Drive, including folders. */
  public final Map<String, File> files = new LinkedHashMap<>();

  /** The sign-ins the app asked Google to cancel. */
  public final List<String> revoked = new ArrayList<>();

  /** The email address of the account that signs in. */
  public String email = "name@gmail.com";

  /** Whether the user allows access to Google Drive when signing in. */
  public boolean grantDrive = true;

  /** Whether the user clicks Cancel instead of Allow. */
  public boolean denySignIn = false;

  /** The permissions the app asked for when last signing in. */
  public volatile String requestedScope;

  /** How many backups have been uploaded. */
  public int uploads;

  /** How many backups have been downloaded. */
  public int downloads;

  private final HttpServer server;
  private final String base;
  private final GoogleDrive previous = DriveBackup.drive;
  private int nextId = 1;
  private int nextToken = 1;
  private final Map<String, String> challenges = new LinkedHashMap<>();
  private final List<String> refreshTokens = new ArrayList<>();

  private FakeGoogle() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    base = "http://127.0.0.1:" + server.getAddress().getPort();
    server.createContext("/", exchange -> {
      try {
        handle(exchange);
      } catch (RuntimeException e) {
        e.printStackTrace();
        reply(exchange, 500, "{}");
      }
    });
    server.start();
  }

  /**
   * Starts a fake Google and points the app at it.
   *
   * @return the fake; call {@link #close()} when done
   */
  public static FakeGoogle install() {
    try {
      var fake = new FakeGoogle();
      DriveBackup.drive = fake.client(fake.base);
      return fake;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Points the app at a server that isn't there, as when offline.
   */
  public void goOffline() {
    // Nothing listens on port 9 (discard) on test machines.
    DriveBackup.drive = client("http://127.0.0.1:9");
  }

  /** Cancels every sign-in, as when the user removes the app from their Google account. */
  public void revokeAll() {
    refreshTokens.clear();
  }

  /** Stops the fake and points the app back where it was. */
  public void close() {
    DriveBackup.cancelConnect();
    DriveBackup.drive = previous;
    server.stop(0);
  }

  /**
   * Returns a browser that signs in as {@link #email} when given Google's
   * sign-in page, by going straight to the address Google would send it
   * back to.
   */
  public java.util.function.Consumer<String> browser() {
    return url -> new Thread(() -> {
      var params = GoogleDrive.parseQuery(URI.create(url).getRawQuery());
      requestedScope = params.get("scope");
      String code;
      synchronized (this) {
        code = "code-" + nextToken++;
        challenges.put(code, params.get("code_challenge"));
      }
      var query = denySignIn
          ? "error=access_denied&state=" + params.get("state")
          : "code=" + code + "&state=" + params.get("state");
      try {
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(params.get("redirect_uri") + "/?" + query)).build(),
            BodyHandlers.discarding());
      } catch (IOException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }).start();
  }

  /** Returns the files in the backups folder, by name. */
  public List<String> backupNames() {
    var folder = files.values().stream()
        .filter(f -> f.folder && f.name.equals(GoogleDrive.FOLDER_NAME))
        .findFirst();
    if (folder.isEmpty()) {
      return List.of();
    }
    return files.values().stream()
        .filter(f -> folder.get().id.equals(f.parent))
        .map(f -> f.name)
        .sorted()
        .collect(Collectors.toList());
  }

  /** Adds an empty file to the backups folder, creating the folder if needed. */
  public void addBackup(String name) {
    addBackup(name, new byte[0]);
  }

  /** Adds a file to the backups folder, creating the folder if needed. */
  public synchronized void addBackup(String name, byte[] content) {
    var folder = files.values().stream()
        .filter(f -> f.folder && f.name.equals(GoogleDrive.FOLDER_NAME))
        .findFirst()
        .orElseGet(() -> add(GoogleDrive.FOLDER_NAME, "root", true, new byte[0]));
    add(name, folder.id, false, content);
  }

  private GoogleDrive client(String url) {
    return new GoogleDrive("client-id", "client-secret", url + "/auth", url + "/token", url + "/revoke",
        url + "/drive/v3", url + "/upload/drive/v3");
  }

  private synchronized void handle(HttpExchange exchange) throws IOException {
    var path = exchange.getRequestURI().getPath();
    var method = exchange.getRequestMethod();
    var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);

    if (path.equals("/token")) {
      token(exchange, GoogleDrive.parseQuery(body));
      return;
    }
    if (path.equals("/revoke")) {
      var token = GoogleDrive.parseQuery(body).get("token");
      revoked.add(token);
      refreshTokens.remove(token);
      reply(exchange, 200, "{}");
      return;
    }
    if (!"Bearer access-token".equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
      reply(exchange, 401, "{\"error\":{\"message\":\"Invalid Credentials\"}}");
      return;
    }
    if (path.equals("/drive/v3/about") && method.equals("GET")) {
      reply(exchange, 200, "{\"user\":{\"emailAddress\":" + Json.quote(email) + "}}");
      return;
    }
    if (path.equals("/drive/v3/files") && method.equals("GET")) {
      var query = GoogleDrive.parseQuery(exchange.getRequestURI().getRawQuery()).get("q");
      var matches = files.values().stream()
          .filter(f -> query.contains("mimeType = 'application/vnd.google-apps.folder'")
              ? f.folder && query.contains("name = '" + f.name + "'")
              : query.startsWith("'" + f.parent + "' in parents"))
          .map(f -> "{\"id\":" + Json.quote(f.id) + ",\"name\":" + Json.quote(f.name) + "}")
          .collect(Collectors.joining(","));
      reply(exchange, 200, "{\"files\":[" + matches + "]}");
      return;
    }
    if (path.equals("/drive/v3/files") && method.equals("POST")) {
      var metadata = Json.parseObject(new String(body.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8));
      var folder = add((String) metadata.get("name"), "root", true, new byte[0]);
      reply(exchange, 200, "{\"id\":" + Json.quote(folder.id) + "}");
      return;
    }
    if (path.startsWith("/drive/v3/files/") && method.equals("GET")
        && "media".equals(GoogleDrive.parseQuery(exchange.getRequestURI().getRawQuery()).get("alt"))) {
      var file = files.get(path.substring("/drive/v3/files/".length()));
      if (file == null) {
        reply(exchange, 404, "{}");
        return;
      }
      downloads++;
      exchange.sendResponseHeaders(200, file.content.length);
      try (var out = exchange.getResponseBody()) {
        out.write(file.content);
      }
      return;
    }
    if (path.startsWith("/drive/v3/files/") && method.equals("DELETE")) {
      files.remove(path.substring("/drive/v3/files/".length()));
      reply(exchange, 204, "");
      return;
    }
    if (path.equals("/upload/drive/v3/files") && method.equals("POST")) {
      var boundary = exchange.getRequestHeaders().getFirst("Content-Type").split("boundary=")[1];
      var parts = body.split("--" + boundary);
      var metadata = Json.parseObject(parts[1].substring(parts[1].indexOf("\r\n\r\n") + 4).trim());
      var content = parts[2].substring(parts[2].indexOf("\r\n\r\n") + 4, parts[2].length() - 2);
      var parents = (List<?>) metadata.get("parents");
      var file = add((String) metadata.get("name"), (String) parents.get(0), false,
          content.getBytes(StandardCharsets.ISO_8859_1));
      uploads++;
      reply(exchange, 200, "{\"id\":" + Json.quote(file.id) + "}");
      return;
    }
    reply(exchange, 404, "{}");
  }

  private void token(HttpExchange exchange, Map<String, String> params) throws IOException {
    if ("authorization_code".equals(params.get("grant_type"))) {
      var challenge = challenges.remove(params.get("code"));
      if (challenge == null || !challenge.equals(sha256(params.get("code_verifier")))) {
        reply(exchange, 400, "{\"error\":\"invalid_grant\"}");
        return;
      }
      var refreshToken = "refresh-" + nextToken++;
      refreshTokens.add(refreshToken);
      var scope = grantDrive ? GoogleDrive.DRIVE_SCOPE : "";
      reply(exchange, 200, "{\"access_token\":\"access-token\",\"refresh_token\":" + Json.quote(refreshToken)
          + ",\"scope\":" + Json.quote(scope) + "}");
      return;
    }
    if (!refreshTokens.contains(params.get("refresh_token"))) {
      reply(exchange, 400, "{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}");
      return;
    }
    reply(exchange, 200, "{\"access_token\":\"access-token\"}");
  }

  private File add(String name, String parent, boolean folder, byte[] content) {
    var file = new File("id" + nextId++, name, parent, folder, content);
    files.put(file.id, file);
    return file;
  }

  private static void reply(HttpExchange exchange, int status, String json) throws IOException {
    var bytes = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
    try (var out = exchange.getResponseBody()) {
      if (status != 204) {
        out.write(bytes);
      }
    }
  }

  private static String sha256(String text) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

}
