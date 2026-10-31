package com.amarullz.androidtv.animetvjmto;

import android.net.Uri;
import android.util.Log;

import com.amarullz.androidtv.animetvjmto.utils.GotakuCrypto;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class GotakuProxy {
    private static final String TAG = "GotakuProxy";
    private static GotakuProxy instance;

    private ServerSocket serverSocket;
    private int port = 0;
    private boolean isRunning = false;
    private ExecutorService threadPool;
    private final OkHttpClient httpClient;

    private static final Pattern URI_REGEX = Pattern.compile("URI=\"([^\"]+)\"");

    public GotakuProxy() {
        httpClient = AnimeApi.httpClient != null ? AnimeApi.httpClient : new OkHttpClient.Builder().build();
    }

    public static synchronized GotakuProxy getInstance() {
        if (instance == null) {
            instance = new GotakuProxy();
        }
        return instance;
    }

    public synchronized void startServer() {
        if (isRunning) return;
        try {
            serverSocket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
            port = serverSocket.getLocalPort();
            isRunning = true;
            threadPool = Executors.newCachedThreadPool();

            threadPool.execute(() -> {
                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket socket = serverSocket.accept();
                        threadPool.execute(() -> handleConnection(socket));
                    } catch (Exception e) {
                        if (!isRunning) break;
                    }
                }
            });
            Log.i(TAG, "GotakuProxy started on 127.0.0.1:" + port);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start GotakuProxy", e);
        }
    }

    public synchronized void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
        port = 0;
    }

    public int getPort() {
        return port;
    }

    public String createProxyPlaylistUrl(String originalUrl, String keySeed, int segmentBytes) {
        if (port == 0) startServer();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("http://127.0.0.1:").append(port).append("/proxy/playlist.m3u8?url=")
              .append(URLEncoder.encode(originalUrl, "UTF-8"));
            if (keySeed != null && !keySeed.isEmpty()) {
                sb.append("&key=").append(URLEncoder.encode(keySeed, "UTF-8"))
                  .append("&sb=").append(segmentBytes);
            }
            sb.append("&m3u8=1");
            return sb.toString();
        } catch (Exception e) {
            return originalUrl;
        }
    }

    private void handleConnection(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String reqLine = reader.readLine();
            if (reqLine == null) return;

            String[] parts = reqLine.split(" ");
            if (parts.length < 2) return;
            String path = parts[1];

            long rangeStart = 0;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("range:")) {
                    String val = line.substring(6).trim();
                    if (val.startsWith("bytes=")) {
                        String r = val.substring(6).split("-")[0];
                        try {
                            rangeStart = Long.parseLong(r);
                        } catch (Exception ignored) {}
                    }
                }
            }

            Uri uri = Uri.parse("http://127.0.0.1" + path);
            String upstreamUrl = uri.getQueryParameter("url");
            String keySeed = uri.getQueryParameter("key");
            String sbStr = uri.getQueryParameter("sb");
            int segmentBytes = (sbStr != null) ? Integer.parseInt(sbStr) : 0;
            boolean isM3u8 = "1".equals(uri.getQueryParameter("m3u8"));

            if (upstreamUrl == null || !upstreamUrl.startsWith("http")) {
                sendResponse(out, "HTTP/1.1 400 Bad Request\r\n\r\n", null);
                return;
            }

            if (isM3u8) {
                serveManifest(out, upstreamUrl, keySeed, segmentBytes);
            } else {
                serveSegment(out, upstreamUrl, keySeed, segmentBytes, rangeStart);
            }
        } catch (Exception e) {
            Log.w(TAG, "handleConnection error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void serveManifest(OutputStream out, String url, String keySeed, int segmentBytes) {
        try {
            Request.Builder reqB = new Request.Builder().url(url)
                    .header("Accept", "*/*")
                    .header("Referer", "https://gotaku.to/");
            Response resp = httpClient.newCall(reqB.build()).execute();
            if (!resp.isSuccessful()) {
                sendResponse(out, "HTTP/1.1 " + resp.code() + " Error\r\n\r\n", null);
                resp.close();
                return;
            }

            byte[] bodyBytes = resp.body().bytes();
            resp.close();

            String m3u8Text;
            if (keySeed != null && !keySeed.isEmpty() && bodyBytes.length > 28) {
                try {
                    byte[] keySeedBytes = GotakuCrypto.b64UrlDecode(keySeed);
                    Uri httpUri = Uri.parse(url);
                    String token = httpUri.getQueryParameter("token");
                    if (token == null) {
                        String p = httpUri.getPath();
                        String[] segs = p != null ? p.split("/") : new String[0];
                        token = segs.length > 3 ? segs[3] : "";
                    }
                    byte[] plKey = GotakuCrypto.hkdfSha256(keySeedBytes, token.getBytes(StandardCharsets.UTF_8),
                            "pl|v1".getBytes(StandardCharsets.UTF_8), 32);

                    byte[] iv = new byte[12];
                    byte[] ct = new byte[bodyBytes.length - 12];
                    System.arraycopy(bodyBytes, 0, iv, 0, 12);
                    System.arraycopy(bodyBytes, 12, ct, 0, ct.length);

                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(plKey, "AES"), new GCMParameterSpec(128, iv));
                    m3u8Text = new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    m3u8Text = new String(bodyBytes, StandardCharsets.UTF_8);
                }
            } else {
                m3u8Text = new String(bodyBytes, StandardCharsets.UTF_8);
            }

            String rewritten = rewriteManifest(m3u8Text, url, keySeed, segmentBytes);
            byte[] outBytes = rewritten.getBytes(StandardCharsets.UTF_8);
            String headers = "HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/vnd.apple.mpegurl\r\n"
                    + "Content-Length: " + outBytes.length + "\r\n"
                    + "Access-Control-Allow-Origin: *\r\n\r\n";
            sendResponse(out, headers, outBytes);
        } catch (Exception e) {
            Log.e(TAG, "serveManifest failed", e);
        }
    }

    private String rewriteManifest(String text, String parentUrl, String keySeed, int segmentBytes) {
        boolean isMaster = text.contains("#EXT-X-STREAM-INF");
        Uri parent = Uri.parse(parentUrl);
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append(line).append("\n");
            } else if (trimmed.startsWith("#")) {
                if (trimmed.contains("URI=\"")) {
                    Matcher m = URI_REGEX.matcher(trimmed);
                    if (m.find()) {
                        String rawUri = m.group(1);
                        String resolved = resolveUri(parent, rawUri);
                        String proxyUrl = buildProxyUrl(resolved, keySeed, segmentBytes, trimmed.contains("TYPE=SUBTITLES"));
                        sb.append(m.replaceFirst("URI=\"" + proxyUrl + "\"")).append("\n");
                        continue;
                    }
                }
                sb.append(line).append("\n");
            } else {
                String resolved = resolveUri(parent, trimmed);
                String proxyUrl = buildProxyUrl(resolved, keySeed, segmentBytes, isMaster);
                sb.append(proxyUrl).append("\n");
            }
        }
        return sb.toString();
    }

    private String resolveUri(Uri parent, String relative) {
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            return relative;
        }
        if (relative.startsWith("/")) {
            return parent.getScheme() + "://" + parent.getHost() + relative;
        }
        String p = parent.getPath();
        if (p == null) p = "/";
        int lastSlash = p.lastIndexOf('/');
        String base = (lastSlash != -1) ? p.substring(0, lastSlash + 1) : "/";
        return parent.getScheme() + "://" + parent.getHost() + base + relative;
    }

    private String buildProxyUrl(String originalUrl, String keySeed, int segmentBytes, boolean isM3u8) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("http://127.0.0.1:").append(port)
              .append(isM3u8 ? "/proxy/playlist.m3u8" : "/proxy/segment.ts")
              .append("?url=").append(URLEncoder.encode(originalUrl, "UTF-8"));
            if (keySeed != null && !keySeed.isEmpty()) {
                sb.append("&key=").append(URLEncoder.encode(keySeed, "UTF-8"))
                  .append("&sb=").append(segmentBytes);
            }
            if (isM3u8) sb.append("&m3u8=1");
            return sb.toString();
        } catch (Exception e) {
            return originalUrl;
        }
    }

    private void serveSegment(OutputStream out, String url, String keySeed, int segmentBytes, long rangeStart) {
        try {
            Request.Builder reqB = new Request.Builder().url(url)
                    .header("Accept", "*/*")
                    .header("Referer", "https://gotaku.to/");
            if (rangeStart > 0) {
                reqB.header("Range", "bytes=" + rangeStart + "-");
            }
            Response resp = httpClient.newCall(reqB.build()).execute();
            if (!resp.isSuccessful()) {
                sendResponse(out, "HTTP/1.1 " + resp.code() + " Error\r\n\r\n", null);
                resp.close();
                return;
            }

            ResponseBody body = resp.body();
            if (body == null) {
                resp.close();
                return;
            }

            long contentLength = body.contentLength();
            String codeStr = (resp.code() == 206) ? "206 Partial Content" : "200 OK";

            StringBuilder headerSb = new StringBuilder();
            headerSb.append("HTTP/1.1 ").append(codeStr).append("\r\n")
                    .append("Content-Type: video/mp2t\r\n")
                    .append("Accept-Ranges: bytes\r\n");
            if (contentLength > 0) {
                headerSb.append("Content-Length: ").append(contentLength).append("\r\n");
            }
            String contentRange = resp.header("Content-Range");
            if (contentRange != null) {
                headerSb.append("Content-Range: ").append(contentRange).append("\r\n");
            }
            headerSb.append("Access-Control-Allow-Origin: *\r\n\r\n");
            out.write(headerSb.toString().getBytes(StandardCharsets.UTF_8));

            byte[] keyBytes = (keySeed != null && !keySeed.isEmpty()) ? GotakuCrypto.b64UrlDecode(keySeed) : null;
            Cipher cipher = null;
            if (keyBytes != null && segmentBytes > 0 && rangeStart < segmentBytes) {
                cipher = GotakuCrypto.createSegmentCipher(keyBytes, url, true, rangeStart);
            }

            InputStream inStream = body.byteStream();
            byte[] buf = new byte[8192];
            int read;
            long currentPos = rangeStart;

            while ((read = inStream.read(buf)) != -1) {
                if (cipher != null && currentPos < segmentBytes) {
                    int toXor = (int) Math.min(read, segmentBytes - currentPos);
                    byte[] keystream = cipher.update(new byte[toXor]);
                    for (int i = 0; i < toXor; i++) {
                        buf[i] = (byte) (buf[i] ^ keystream[i]);
                    }
                    currentPos += read;
                }
                out.write(buf, 0, read);
            }
            out.flush();
            resp.close();
        } catch (Exception e) {
            Log.w(TAG, "serveSegment error: " + e.getMessage());
        }
    }

    private void sendResponse(OutputStream out, String headers, byte[] body) {
        try {
            out.write(headers.getBytes(StandardCharsets.UTF_8));
            if (body != null) {
                out.write(body);
            }
            out.flush();
        } catch (Exception ignored) {}
    }
}
