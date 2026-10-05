package org.lfps.mailboxes.drive;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.lfps.mailboxes.drive.DriveException.Problem;
import org.lfps.mailboxes.util.Json;

/**
 * Talks to Google: signing in through the browser, and uploading backups to
 * (and downloading them from) a "Mailbox Manager Backups" folder in the
 * user's Google Drive. The app asks
 * only for access to files it creates itself, so it can't see anything else
 * in the user's Drive.
 *
 * <p>Signing in follows Google's steps for desktop apps: the browser is sent
 * to Google, and once the user clicks Allow, Google sends it back to a small
 * web server the app runs on this computer for the moment.
 */
public final class GoogleDrive {

  /** The name of the folder backups are uploaded to. */
  public static final String FOLDER_NAME = "Mailbox Manager Backups";

  /** The permission the app asks for: access only to files it creates in the user's Google Drive. */
  static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.file";

  /** The type Google Drive gives folders. */
  private static final String FOLDER_TYPE = "application/vnd.google-apps.folder";

  /** The file holding the app's Google client ID and secret; see the README. */
  private static final String CREDENTIALS_FILE = "google-oauth.properties";

  /** How long to wait for the user to finish signing in before giving up. */
  private static final long SIGN_IN_MINUTES = 10;

  /** How long to wait for Google to answer an ordinary request. */
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  /** How long to wait for a backup to upload or download, which can take a while on a slow connection. */
  private static final Duration UPLOAD_TIMEOUT = Duration.ofMinutes(5);

  /** Makes the unguessable values used while signing in. */
  private static final SecureRandom RANDOM = new SecureRandom();

  /** The app's Google client ID, which tells Google which app is asking. */
  private final String clientId;

  /** The app's Google client secret. For desktop apps Google doesn't treat it as secret. */
  private final String clientSecret;

  /** The address of Google's sign-in page. */
  private final String authEndpoint;

  /** The address that turns a sign-in into a token for using Google Drive. */
  private final String tokenEndpoint;

  /** The address that cancels a sign-in. */
  private final String revokeEndpoint;

  /** Where Google Drive requests are sent, apart from uploads. */
  private final String apiBase;

  /** Where uploads to Google Drive are sent. */
  private final String uploadBase;

  /** Sends the requests to Google. */
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(15))
      .build();

  /**
   * Makes a client for Google's servers at the given addresses. Tests pass
   * addresses of a fake server.
   *
   * @param clientId the app's Google client ID
   * @param clientSecret the app's Google client secret
   * @param authEndpoint the address of Google's sign-in page
   * @param tokenEndpoint the address that issues tokens
   * @param revokeEndpoint the address that cancels a sign-in
   * @param apiBase where Google Drive requests are sent
   * @param uploadBase where uploads are sent
   */
  GoogleDrive(String clientId, String clientSecret, String authEndpoint, String tokenEndpoint,
      String revokeEndpoint, String apiBase, String uploadBase) {
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authEndpoint = authEndpoint;
    this.tokenEndpoint = tokenEndpoint;
    this.revokeEndpoint = revokeEndpoint;
    this.apiBase = apiBase;
    this.uploadBase = uploadBase;
  }

  /**
   * Returns a client for Google's real servers, using the client ID and
   * secret built into the app.
   *
   * @return the client, or {@code null} if this build of the app has no
   *     Google client ID, as in builds made without one
   */
  static GoogleDrive standard() {
    var props = new Properties();
    try (InputStream in = GoogleDrive.class.getResourceAsStream(CREDENTIALS_FILE)) {
      if (in == null) {
        return null;
      }
      props.load(in);
    } catch (IOException e) {
      return null;
    }
    var id = props.getProperty("client_id", "").trim();
    var secret = props.getProperty("client_secret", "").trim();
    if (id.isEmpty()) {
      return null;
    }
    return new GoogleDrive(id, secret,
        "https://accounts.google.com/o/oauth2/v2/auth",
        "https://oauth2.googleapis.com/token",
        "https://oauth2.googleapis.com/revoke",
        "https://www.googleapis.com/drive/v3",
        "https://www.googleapis.com/upload/drive/v3");
  }

  /**
   * Starts signing in: starts the web server Google sends the browser back
   * to, and returns the address of Google's sign-in page for the browser to
   * open.
   *
   * @return the sign-in in progress
   * @throws DriveException if the web server can't be started
   */
  SignIn startSignIn() {
    try {
      return new SignIn();
    } catch (IOException e) {
      throw new DriveException(Problem.OTHER, "Couldn't start signing in: " + e.getMessage(), e);
    }
  }

  /**
   * A sign-in waiting for the user to finish in the browser.
   */
  final class SignIn {

    /** The web server on this computer that Google sends the browser back to. */
    private final HttpServer server;

    /** The address of that web server, which Google sends the browser back to. */
    private final String redirectUri;

    /**
     * A random value sent to Google and checked in its reply, so replies that
     * aren't to this sign-in are ignored.
     */
    private final String state = randomToken();

    /** A random value proving to Google that the reply came back to the app that started the sign-in. */
    private final String verifier = randomToken();

    /** Completes when the user finishes signing in, or it fails. */
    private final CompletableFuture<DriveAccount> result = new CompletableFuture<>();

    /**
     * Starts the web server, which stops itself after the sign-in finishes or
     * after {@value GoogleDrive#SIGN_IN_MINUTES} minutes.
     *
     * @throws IOException if the web server can't be started
     */
    private SignIn() throws IOException {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      redirectUri = "http://127.0.0.1:" + server.getAddress().getPort();
      server.createContext("/", this::handle);
      server.start();
      // Stopped off the server's own thread, which may be the one finishing the sign-in.
      result.orTimeout(SIGN_IN_MINUTES, TimeUnit.MINUTES)
          .whenCompleteAsync((account, error) -> server.stop(0));
    }

    /**
     * Returns the address of Google's sign-in page.
     *
     * @return the address to open in the browser
     */
    String url() {
      var params = new LinkedHashMap<String, String>();
      params.put("client_id", clientId);
      params.put("redirect_uri", redirectUri);
      params.put("response_type", "code");
      // Only one permission, so Google's page has nothing to uncheck: with
      // more than one, it shows a checkbox for each that the user must tick.
      params.put("scope", DRIVE_SCOPE);
      params.put("state", state);
      params.put("code_challenge", challenge(verifier));
      params.put("code_challenge_method", "S256");
      // Asks for a sign-in that lasts, and asks again even if the user
      // allowed it before, so Google always sends one.
      params.put("access_type", "offline");
      params.put("prompt", "consent");
      return authEndpoint + "?" + form(params);
    }

    /**
     * Returns the signed-in account once the user finishes.
     *
     * @return completes with the account, or with a {@link DriveException}
     *     if signing in failed, was cancelled, or took too long
     */
    CompletableFuture<DriveAccount> result() {
      return result;
    }

    /** Gives up on signing in and stops the web server. */
    void cancel() {
      result.completeExceptionally(new DriveException(Problem.CANCELLED, "Signing in was cancelled."));
    }

    /**
     * Handles the browser arriving back from Google: shows the user a page
     * saying whether it worked, and finishes the sign-in.
     *
     * @param exchange the browser's request
     * @throws IOException if the page can't be sent to the browser
     */
    private void handle(HttpExchange exchange) throws IOException {
      var params = parseQuery(exchange.getRequestURI().getRawQuery());
      // Ignore anything but Google's reply, such as the browser asking for an icon.
      if (!state.equals(params.get("state")) || result.isDone()) {
        respond(exchange, 404, "Not found", "");
        return;
      }
      // Each reply goes to the browser before the result is set, since
      // setting it stops this server.
      if (params.containsKey("error")) {
        respond(exchange, 200, "Not connected",
            "Mailbox Manager wasn't connected to Google Drive. You can close this tab.");
        var cancelled = "access_denied".equals(params.get("error"));
        result.completeExceptionally(cancelled
            ? new DriveException(Problem.CANCELLED, "Signing in was cancelled.")
            : new DriveException(Problem.OTHER, "Google couldn't sign you in (" + params.get("error") + ")."));
        return;
      }
      try {
        var account = exchangeCode(params.getOrDefault("code", ""), verifier, redirectUri);
        respond(exchange, 200, "Connected",
            "Mailbox Manager is connected to Google Drive. You can close this tab and go back to the app.");
        result.complete(account);
      } catch (DriveException e) {
        respond(exchange, 200, "Not connected", e.getMessage() + " You can close this tab.");
        result.completeExceptionally(e);
      }
    }
  }

  /**
   * Uploads a daily backup to the backups folder if it isn't there yet, then
   * deletes all but the newest daily backups there.
   *
   * @param refreshToken the saved sign-in
   * @param backup the backup file to upload
   * @param backupsToKeep how many daily backups to keep in the folder
   * @param isDailyBackup whether a file name is that of a daily backup; only
   *     those are deleted
   * @throws DriveException if the upload fails
   */
  void upload(String refreshToken, Path backup, int backupsToKeep, Predicate<String> isDailyBackup) {
    var token = accessToken(refreshToken);
    var folderId = findOrCreateFolder(token);
    var files = listFolder(token, folderId);
    var name = backup.getFileName().toString();
    if (files.stream().noneMatch(f -> f.name.equals(name))) {
      uploadFile(token, folderId, backup);
      files.add(new DriveFile("", name));
    }
    var old = files.stream()
        .filter(f -> isDailyBackup.test(f.name))
        .sorted(Comparator.comparing((DriveFile f) -> f.name).reversed())
        .skip(Math.max(1, backupsToKeep))
        .collect(Collectors.toList());
    for (var file : old) {
      send(authorized(token, apiBase + "/files/" + file.id).DELETE(), REQUEST_TIMEOUT);
    }
  }

  /**
   * Returns the names of the files in the backups folder, newest first.
   *
   * @param refreshToken the saved sign-in
   * @return the names, or an empty list if there's no backups folder
   * @throws DriveException if they can't be listed
   */
  List<String> backupNames(String refreshToken) {
    var token = accessToken(refreshToken);
    var folderId = findFolder(token);
    if (folderId == null) {
      return List.of();
    }
    return listFolder(token, folderId).stream()
        .map(f -> f.name)
        .sorted(Comparator.reverseOrder())
        .collect(Collectors.toList());
  }

  /**
   * Downloads a file from the backups folder.
   *
   * @param refreshToken the saved sign-in
   * @param name the file's name
   * @param target where to save it; replaced if it exists
   * @throws DriveException if it can't be downloaded; nothing is left at
   *     {@code target}
   */
  void download(String refreshToken, String name, Path target) {
    var token = accessToken(refreshToken);
    var folderId = findFolder(token);
    var file = folderId == null ? null : listFolder(token, folderId).stream()
        .filter(f -> f.name.equals(name))
        .findFirst()
        .orElse(null);
    if (file == null) {
      throw new DriveException(Problem.OTHER, name + " isn't in the " + FOLDER_NAME + " folder in Google Drive.");
    }
    var partial = target.resolveSibling(target.getFileName() + ".partial");
    try {
      Files.createDirectories(target.getParent());
      var response = http.send(authorized(token, apiBase + "/files/" + file.id + "?alt=media")
          .timeout(UPLOAD_TIMEOUT).GET().build(), BodyHandlers.ofFile(partial));
      if (response.statusCode() == 401) {
        throw new DriveException(Problem.SIGNED_OUT, "Mailbox Manager is no longer signed in to Google Drive.");
      }
      if (response.statusCode() != 200) {
        throw googleError(response.statusCode(), Map.of());
      }
      Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      throw new DriveException(Problem.OFFLINE, "Couldn't download " + name + " from Google Drive. "
          + "Check that this computer is connected to the internet.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DriveException(Problem.CANCELLED, "The download was stopped.", e);
    } finally {
      try {
        Files.deleteIfExists(partial);
      } catch (IOException ignored) {
        // Only a leftover partial download.
      }
    }
  }

  /**
   * Tells Google to cancel a sign-in. Problems are ignored, since the sign-in
   * is forgotten either way.
   *
   * @param refreshToken the saved sign-in
   */
  void revoke(String refreshToken) {
    try {
      http.send(HttpRequest.newBuilder(URI.create(revokeEndpoint))
          .timeout(REQUEST_TIMEOUT)
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(BodyPublishers.ofString(form(Map.of("token", refreshToken))))
          .build(), BodyHandlers.discarding());
    } catch (IOException e) {
      // Offline; the user can remove access from their Google account instead.
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Trades the one-time code Google sends back for a lasting sign-in, checking
   * the user allowed access to Google Drive.
   *
   * @param code the one-time code from Google
   * @param verifier the random value sent with the sign-in
   * @param redirectUri the address Google sent the browser back to
   * @return the signed-in account
   * @throws DriveException if Google refuses, or the user didn't allow access
   */
  private DriveAccount exchangeCode(String code, String verifier, String redirectUri) {
    var params = new LinkedHashMap<String, String>();
    params.put("grant_type", "authorization_code");
    params.put("code", code);
    params.put("code_verifier", verifier);
    params.put("redirect_uri", redirectUri);
    var reply = tokenRequest(params);

    var scope = String.valueOf(reply.get("scope"));
    if (!List.of(scope.split(" ")).contains(DRIVE_SCOPE)) {
      throw new DriveException(Problem.OTHER, "Mailbox Manager wasn't given permission to save files in "
          + "your Google Drive. Please connect again, and allow access to Google Drive.");
    }
    var refreshToken = reply.get("refresh_token");
    if (!(refreshToken instanceof String)) {
      throw new DriveException(Problem.OTHER, "Google didn't finish signing you in. Please try again.");
    }
    return new DriveAccount(emailOf(reply.get("access_token")), (String) refreshToken, null);
  }

  /**
   * Gets a short-lived token for using Google Drive from the saved sign-in.
   *
   * @param refreshToken the saved sign-in
   * @return the token
   * @throws DriveException if Google refuses, for example because the sign-in
   *     was removed
   */
  private String accessToken(String refreshToken) {
    var params = new LinkedHashMap<String, String>();
    params.put("grant_type", "refresh_token");
    params.put("refresh_token", refreshToken);
    var token = tokenRequest(params).get("access_token");
    if (!(token instanceof String)) {
      throw new DriveException(Problem.OTHER, "Google didn't accept the sign-in. Please try again later.");
    }
    return (String) token;
  }

  /**
   * Sends a request to Google's token address, adding the app's client ID and
   * secret.
   *
   * @param params the request's fields
   * @return Google's reply
   * @throws DriveException if Google refuses or can't be reached
   */
  private Map<String, Object> tokenRequest(Map<String, String> params) {
    var body = new LinkedHashMap<>(params);
    body.put("client_id", clientId);
    body.put("client_secret", clientSecret);
    var request = HttpRequest.newBuilder(URI.create(tokenEndpoint))
        .timeout(REQUEST_TIMEOUT)
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(BodyPublishers.ofString(form(body)))
        .build();
    var response = exchange(request);
    var reply = parseReply(response.body());
    if (response.statusCode() == 400 && "invalid_grant".equals(reply.get("error"))) {
      // The sign-in expired, was removed from the Google account, or (while
      // signing in) the one-time code was already used.
      throw new DriveException(Problem.SIGNED_OUT, "Mailbox Manager is no longer signed in to Google Drive.");
    }
    if (response.statusCode() != 200) {
      throw googleError(response.statusCode(), reply);
    }
    return reply;
  }

  /**
   * Returns the id of the backups folder.
   *
   * @param token a token for using Google Drive
   * @return the folder's id, or {@code null} if there isn't one
   * @throws DriveException if Google Drive can't be searched
   */
  private String findFolder(String token) {
    var query = "name = '" + FOLDER_NAME + "' and mimeType = '" + FOLDER_TYPE
        + "' and 'root' in parents and trashed = false";
    var folders = list(token, query);
    return folders.isEmpty() ? null : folders.get(0).id;
  }

  /**
   * Returns the id of the backups folder, creating it if there isn't one.
   *
   * @param token a token for using Google Drive
   * @return the folder's id
   * @throws DriveException if the folder can't be found or created
   */
  private String findOrCreateFolder(String token) {
    var existing = findFolder(token);
    if (existing != null) {
      return existing;
    }
    var metadata = "{\"name\":" + Json.quote(FOLDER_NAME) + ",\"mimeType\":" + Json.quote(FOLDER_TYPE) + "}";
    var created = parseReply(send(authorized(token, apiBase + "/files?fields=id")
        .header("Content-Type", "application/json; charset=UTF-8")
        .POST(BodyPublishers.ofString(metadata)), REQUEST_TIMEOUT));
    return String.valueOf(created.get("id"));
  }

  /**
   * Returns the files in a folder.
   *
   * @param token a token for using Google Drive
   * @param folderId the folder's id
   * @return the files, in no particular order
   * @throws DriveException if they can't be listed
   */
  private List<DriveFile> listFolder(String token, String folderId) {
    return list(token, "'" + folderId.replace("'", "\\'") + "' in parents and trashed = false");
  }

  /**
   * Returns the files matching a Google Drive search, a page at a time until
   * all are fetched.
   *
   * @param token a token for using Google Drive
   * @param query the search, in Google Drive's query language
   * @return the files
   * @throws DriveException if the search fails
   */
  private List<DriveFile> list(String token, String query) {
    var files = new ArrayList<DriveFile>();
    String pageToken = null;
    do {
      var params = new LinkedHashMap<String, String>();
      params.put("q", query);
      params.put("fields", "nextPageToken,files(id,name)");
      params.put("pageSize", "1000");
      params.put("spaces", "drive");
      if (pageToken != null) {
        params.put("pageToken", pageToken);
      }
      var reply = parseReply(send(authorized(token, apiBase + "/files?" + form(params)).GET(), REQUEST_TIMEOUT));
      var page = reply.get("files");
      if (page instanceof List) {
        for (var item : (List<?>) page) {
          if (item instanceof Map) {
            var file = (Map<?, ?>) item;
            files.add(new DriveFile(String.valueOf(file.get("id")), String.valueOf(file.get("name"))));
          }
        }
      }
      pageToken = reply.get("nextPageToken") instanceof String ? (String) reply.get("nextPageToken") : null;
    } while (pageToken != null);
    return files;
  }

  /**
   * Uploads a file into a folder, along with its name, in one request.
   *
   * @param token a token for using Google Drive
   * @param folderId the folder's id
   * @param file the file to upload
   * @throws DriveException if the file can't be read or uploaded
   */
  private void uploadFile(String token, String folderId, Path file) {
    var boundary = "mailbox-manager-" + randomToken();
    var metadata = "{\"name\":" + Json.quote(file.getFileName().toString())
        + ",\"parents\":[" + Json.quote(folderId) + "]}";
    var body = new ByteArrayOutputStream();
    try {
      body.write(("--" + boundary + "\r\n"
          + "Content-Type: application/json; charset=UTF-8\r\n\r\n"
          + metadata + "\r\n"
          + "--" + boundary + "\r\n"
          + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
      body.write(Files.readAllBytes(file));
      body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new DriveException(Problem.OTHER, "Couldn't read " + file + ": " + e.getMessage(), e);
    }
    send(authorized(token, uploadBase + "/files?uploadType=multipart&fields=id")
        .header("Content-Type", "multipart/related; boundary=" + boundary)
        .POST(BodyPublishers.ofByteArray(body.toByteArray())), UPLOAD_TIMEOUT);
  }

  /**
   * Starts a request to Google Drive, signed in with a token.
   *
   * @param token a token for using Google Drive
   * @param url the request's address
   * @return the request, ready for its method and body
   */
  private HttpRequest.Builder authorized(String token, String url) {
    return HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Bearer " + token);
  }

  /**
   * Sends a Drive request and returns the reply, failing unless it succeeded.
   *
   * @param request the request
   * @param timeout how long to wait for the reply
   * @return the reply's body
   * @throws DriveException if the request fails, or the app is no longer signed in
   */
  private String send(HttpRequest.Builder request, Duration timeout) {
    var response = exchange(request.timeout(timeout).build());
    var status = response.statusCode();
    if (status == 401) {
      throw new DriveException(Problem.SIGNED_OUT, "Mailbox Manager is no longer signed in to Google Drive.");
    }
    if (status < 200 || status >= 300) {
      throw googleError(status, parseReply(response.body()));
    }
    return response.body();
  }

  /**
   * Sends a request and returns Google's reply, whatever its status.
   *
   * @param request the request
   * @return the reply
   * @throws DriveException if Google can't be reached, or the request was
   *     interrupted
   */
  private HttpResponse<String> exchange(HttpRequest request) {
    try {
      return http.send(request, BodyHandlers.ofString());
    } catch (IOException e) {
      throw new DriveException(Problem.OFFLINE, "Couldn't reach Google Drive. "
          + "Check that this computer is connected to the internet.", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DriveException(Problem.CANCELLED, "The upload was stopped.", e);
    }
  }

  /**
   * Describes a problem Google reported, using its own explanation when it
   * gives one.
   *
   * @param status the reply's HTTP status
   * @param reply the reply's fields
   * @return the problem, to throw
   */
  private static DriveException googleError(int status, Map<String, Object> reply) {
    var detail = "";
    var error = reply.get("error");
    if (error instanceof Map && ((Map<?, ?>) error).get("message") != null) {
      detail = ": " + ((Map<?, ?>) error).get("message");
    } else if (reply.get("error_description") != null) {
      detail = ": " + reply.get("error_description");
    } else if (error != null) {
      detail = ": " + error;
    }
    return new DriveException(Problem.OTHER, "Google Drive reported a problem (error " + status + detail + ").");
  }

  /**
   * Reads a reply from Google as JSON.
   *
   * @param body the reply's body
   * @return its fields, or none if it's empty or isn't JSON
   */
  private static Map<String, Object> parseReply(String body) {
    try {
      return body == null || body.isBlank() ? Map.of() : Json.parseObject(body);
    } catch (IllegalArgumentException e) {
      return Map.of();
    }
  }

  /**
   * Asks Drive for the signed-in user's email address, to show which account backups go to.
   *
   * @param accessToken a token for using Google Drive, as it came in Google's reply
   * @return the address, or "your Google account" if it can't be found
   */
  private String emailOf(Object accessToken) {
    if (accessToken instanceof String) {
      try {
        var about = parseReply(send(authorized((String) accessToken,
            apiBase + "/about?fields=" + URLEncoder.encode("user(emailAddress)", StandardCharsets.UTF_8)).GET(),
            REQUEST_TIMEOUT));
        var user = about.get("user");
        if (user instanceof Map && ((Map<?, ?>) user).get("emailAddress") instanceof String) {
          return (String) ((Map<?, ?>) user).get("emailAddress");
        }
      } catch (DriveException ignored) {
        // Connected all the same; the address is only for show.
      }
    }
    return "your Google account";
  }

  /**
   * Shows a short page in the browser, such as to say signing in worked.
   *
   * @param exchange the browser's request
   * @param status the HTTP status to reply with
   * @param title the page's heading
   * @param message the text under the heading
   * @throws IOException if the page can't be sent
   */
  private static void respond(HttpExchange exchange, int status, String title, String message) throws IOException {
    var html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Mailbox Manager – " + title
        + "</title></head><body style=\"font-family: sans-serif; margin: 3em;\"><h1>" + title
        + "</h1><p>" + escapeHtml(message) + "</p></body></html>";
    var bytes = html.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }

  /**
   * Makes text safe to put in a web page.
   *
   * @param text the text
   * @return the text with {@code &}, {@code <}, and {@code >} escaped
   */
  private static String escapeHtml(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /**
   * Encodes fields as a web form, as in a request body or an address's query.
   *
   * @param params the fields
   * @return the encoded fields, such as {@code a=1&b=2}
   */
  private static String form(Map<String, String> params) {
    return params.entrySet().stream()
        .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
            + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
        .collect(Collectors.joining("&"));
  }

  /**
   * Decodes the query part of an address, such as {@code code=abc&state=xyz}.
   *
   * @param query the query, or {@code null}
   * @return the fields, which are empty if there's no query
   */
  static Map<String, String> parseQuery(String query) {
    var params = new HashMap<String, String>();
    if (query == null || query.isEmpty()) {
      return params;
    }
    for (var pair : query.split("&")) {
      var eq = pair.indexOf('=');
      var key = eq < 0 ? pair : pair.substring(0, eq);
      var value = eq < 0 ? "" : pair.substring(eq + 1);
      params.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
    }
    return params;
  }

  /**
   * Makes a random value that can't be guessed, for use while signing in.
   *
   * @return the value, in a form safe for addresses
   */
  private static String randomToken() {
    var bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /**
   * Makes the value sent to Google that it later checks the verifier against.
   *
   * @param verifier the random verifier
   * @return its SHA-256 hash, in a form safe for addresses
   */
  private static String challenge(String verifier) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** A file in Google Drive. */
  private static final class DriveFile {
    /** The file's id in Google Drive. */
    private final String id;

    /** The file's name. */
    private final String name;

    /**
     * Makes a file.
     *
     * @param id the file's id in Google Drive
     * @param name the file's name
     */
    DriveFile(String id, String name) {
      this.id = id;
      this.name = name;
    }
  }

}
