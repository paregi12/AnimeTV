package com.amarullz.androidtv.animetvjmto;

import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ReAnimeProxy {
    private static final String TAG = "ReAnimeProxy";
    private static ReAnimeProxy instance;

    private ServerSocket serverSocket;
    private int port = 0;
    private boolean isRunning = false;
    private ExecutorService threadPool;
    private final OkHttpClient httpClient;

    private static final String FLIXCLOUD_URL = "https://flixcloud.cc";
    private static final String ENC_DEC_URL = "https://enc-dec.app";
    private static final String DEC_API = ENC_DEC_URL + "/api";

    private static final Pattern URI_REGEX = Pattern.compile("URI=\"([^\"]+)\"");
    private static final Pattern BANDWIDTH_REGEX = Pattern.compile("BANDWIDTH=(\\d+)");
    private static final Pattern AVERAGE_BANDWIDTH_REGEX = Pattern.compile("AVERAGE-BANDWIDTH=(\\d+)");

    private byte[] segmentMask = new byte[]{
        (byte)157, 42, (byte)241, 71, (byte)179, (byte)142, 92, 112,
        (byte)166, 25, (byte)228, 59, (byte)216, 98, 15, (byte)197
    };

    public ReAnimeProxy() {
        httpClient = AnimeApi.httpClient != null ? AnimeApi.httpClient : new OkHttpClient.Builder().build();
    }

    public static synchronized ReAnimeProxy getInstance() {
        if (instance == null) {
            instance = new ReAnimeProxy();
        }
        return instance;
    }

    public synchronized void updateMask(byte[] newMask) {
        if (newMask != null && newMask.length > 0) {
            segmentMask = newMask;
        }
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
            Log.i(TAG, "ReAnimeProxy started on 127.0.0.1:" + port);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start ReAnimeProxy", e);
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

    public String createProxyPlaylistUrl(String originalUrl, String wPayload) {
        if (port == 0) startServer();
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("http://127.0.0.1:").append(port).append("/manifest.m3u8?url=")
              .append(URLEncoder.encode(originalUrl, "UTF-8"));
            if (wPayload != null && !wPayload.isEmpty()) {
                sb.append("&w_payload=").append(URLEncoder.encode(wPayload, "UTF-8"));
            }
            return sb.toString();
        } catch (Exception e) {
            return originalUrl;
        }
    }

    private String createProxyUrl(String originalUrl, String wPayload) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("http://127.0.0.1:").append(port).append("/proxy?url=")
              .append(URLEncoder.encode(originalUrl, "UTF-8"));
            if (wPayload != null && !wPayload.isEmpty()) {
                sb.append("&w_payload=").append(URLEncoder.encode(wPayload, "UTF-8"));
            }
            return sb.toString();
        } catch (Exception e) {
            return originalUrl;
        }
    }

    private String wrapInDecApi(String originalUrl, String wPayload) {
        if (originalUrl.contains(ENC_DEC_URL)) return originalUrl;
        try {
            String encodedUrl = URLEncoder.encode(originalUrl, "UTF-8").replace("+", "%20");
            String encodedWPayload = URLEncoder.encode(wPayload, "UTF-8").replace("+", "%20");
            return DEC_API + "/parse-flixcloud?url=" + encodedUrl + "&w_payload=" + encodedWPayload;
        } catch (Exception e) {
            return originalUrl;
        }
    }

    public static String ensureToken(String segmentUrl, String parentUrl) {
        try {
            Uri segUri = Uri.parse(segmentUrl);
            String token = segUri.getQueryParameter("token");
            if (token == null && parentUrl != null) {
                String curUrl = parentUrl;
                for (int i = 0; i < 3; i++) {
                    Uri pUri = Uri.parse(curUrl);
                    token = pUri.getQueryParameter("token");
                    if (token != null) break;
                    String nested = pUri.getQueryParameter("url");
                    if (nested != null && !nested.isEmpty()) {
                        curUrl = nested;
                    } else {
                        break;
                    }
                }
            }
            if (token != null && segUri.getQueryParameter("token") == null) {
                return segUri.buildUpon().appendQueryParameter("token", token).build().toString();
            }
            return segmentUrl;
        } catch (Exception e) {
            return segmentUrl;
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
            String wPayload = uri.getQueryParameter("w_payload");
            if (wPayload == null) wPayload = "";

            if (upstreamUrl == null || !upstreamUrl.startsWith("http")) {
                sendResponse(out, "HTTP/1.1 400 Bad Request\r\n\r\n", null);
                return;
            }

            boolean isManifest = upstreamUrl.contains(".m3u8") || path.startsWith("/manifest.m3u8");
            if (isManifest) {
                serveManifest(out, upstreamUrl, wPayload);
            } else {
                serveSegment(out, upstreamUrl, rangeStart);
            }
        } catch (Exception e) {
            Log.w(TAG, "handleConnection error: " + e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {}
        }
    }

    private void serveManifest(OutputStream out, String url, String wPayload) {
        try {
            boolean isMasterManifest = url.contains("master.m3u8") ||
                (url.contains("flixcloud.cc") && !url.contains("audio.m3u8") && !url.contains("video.m3u8"));
            String finalUrl = isMasterManifest ? wrapInDecApi(url, wPayload) : url;

            Request.Builder reqB = new Request.Builder().url(finalUrl).header("Accept", "*/*");
            if (finalUrl.contains(ENC_DEC_URL)) {
                reqB.header("Origin", ENC_DEC_URL)
                    .header("Referer", ENC_DEC_URL + "/");
            } else {
                reqB.header("Origin", FLIXCLOUD_URL)
                    .header("Referer", FLIXCLOUD_URL + "/");
            }

            Response resp = null;
            int attempt = 0;
            while (resp == null && attempt < 3) {
                try {
                    resp = httpClient.newCall(reqB.build()).execute();
                } catch (Exception e) {
                    attempt++;
                    if (attempt >= 3) throw e;
                }
            }

            if (resp == null || !resp.isSuccessful()) {
                int code = resp != null ? resp.code() : 500;
                sendResponse(out, "HTTP/1.1 " + code + " Error\r\n\r\n", null);
                if (resp != null) resp.close();
                return;
            }

            String bodyText = resp.body().string();
            resp.close();

            String parentUrl = url;
            if (url.contains("parse-flixcloud")) {
                Uri parsed = Uri.parse(url);
                String qUrl = parsed.getQueryParameter("url");
                if (qUrl != null && !qUrl.isEmpty()) {
                    parentUrl = qUrl;
                }
            }

            String rewritten = rewriteManifest(bodyText, parentUrl, wPayload);
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

    private String rewriteManifest(String text, String parentUrl, String wPayload) {
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append("\n");
                continue;
            }

            if (trimmed.startsWith("#")) {
                String cleanedLine = trimmed;
                if (trimmed.startsWith("#EXT-X-STREAM-INF")) {
                    Matcher bwM = BANDWIDTH_REGEX.matcher(trimmed);
                    Matcher avgBwM = AVERAGE_BANDWIDTH_REGEX.matcher(trimmed);
                    Long peakBw = bwM.find() ? Long.parseLong(bwM.group(1)) : null;
                    Long avgBw = avgBwM.find() ? Long.parseLong(avgBwM.group(1)) : null;

                    if (peakBw != null && peakBw < 100000L) {
                        long finalBw = (avgBw != null && avgBw > 100000L) ? avgBw : peakBw * 1000L;
                        cleanedLine = BANDWIDTH_REGEX.matcher(trimmed).replaceFirst("BANDWIDTH=" + finalBw);
                    }
                }

                if (cleanedLine.contains("URI=\"")) {
                    Matcher m = URI_REGEX.matcher(cleanedLine);
                    if (m.find()) {
                        String uri = m.group(1);
                        String resolved = resolveUri(parentUrl, uri);
                        resolved = ensureToken(resolved, parentUrl);
                        String proxyUri = createProxyUrl(resolved, wPayload);
                        cleanedLine = m.replaceFirst("URI=\"" + Matcher.quoteReplacement(proxyUri) + "\"");
                    }
                }
                sb.append(cleanedLine).append("\n");
            } else {
                String resolved = resolveUri(parentUrl, trimmed);
                resolved = ensureToken(resolved, parentUrl);
                String proxyUrl = createProxyUrl(resolved, wPayload);
                sb.append(proxyUrl).append("\n");
            }
        }
        return sb.toString();
    }

    private String resolveUri(String parentUrl, String relative) {
        if (relative == null || relative.isEmpty()) return "";
        if (relative.startsWith("http://") || relative.startsWith("https://")) {
            return relative;
        }
        try {
            Uri parent = Uri.parse(parentUrl);
            String auth = parent.getAuthority() != null ? parent.getAuthority() : parent.getHost();
            if (relative.startsWith("/")) {
                return parent.getScheme() + "://" + auth + relative;
            }
            String p = parent.getPath();
            if (p == null) p = "/";
            int lastSlash = p.lastIndexOf('/');
            String base = (lastSlash != -1) ? p.substring(0, lastSlash + 1) : "/";
            return parent.getScheme() + "://" + auth + base + relative;
        } catch (Exception e) {
            return relative;
        }
    }

    private int detectHeader(byte[] data) {
        if (data.length >= 12 &&
            data[0] == 0x52 && data[1] == 0x49 && data[2] == 0x46 && data[3] == 0x46 &&
            data[8] == 0x57 && data[9] == 0x45 && data[10] == 0x42 && data[11] == 0x50) {
            return 12;
        }
        if (data.length >= 8 &&
            data[0] == (byte)0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47 &&
            data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return 8;
        }
        return 0;
    }

    private void serveSegment(OutputStream out, String url, long rangeStart) {
        try {
            Request.Builder reqB = new Request.Builder().url(url)
                    .header("Accept", "*/*")
                    .header("Origin", FLIXCLOUD_URL)
                    .header("Referer", FLIXCLOUD_URL + "/");

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

            InputStream source = body.byteStream();
            java.io.BufferedInputStream bis = new java.io.BufferedInputStream(source);

            bis.mark(16);
            byte[] headerPeek = new byte[13];
            int peeked = bis.read(headerPeek, 0, 13);
            bis.reset();

            int headerSize = 0;
            if (peeked >= 8) {
                byte[] actual = new byte[peeked];
                System.arraycopy(headerPeek, 0, actual, 0, peeked);
                headerSize = detectHeader(actual);
            }

            boolean shouldXor = headerSize > 0;
            long origLen = body.contentLength();
            long outLen = (origLen > 0 && headerSize > 0) ? origLen - headerSize : origLen;

            StringBuilder headerSb = new StringBuilder();
            headerSb.append("HTTP/1.1 200 OK\r\n")
                    .append("Content-Type: ").append(shouldXor ? "video/mp2t" : resp.header("Content-Type", "application/octet-stream")).append("\r\n");
            if (outLen > 0) {
                headerSb.append("Content-Length: ").append(outLen).append("\r\n");
            }
            headerSb.append("Access-Control-Allow-Origin: *\r\n\r\n");
            out.write(headerSb.toString().getBytes(StandardCharsets.UTF_8));

            int skipped = 0;
            while (skipped < headerSize) {
                long s = bis.skip(headerSize - skipped);
                if (s <= 0) {
                    if (bis.read() == -1) break;
                    s = 1;
                }
                skipped += s;
            }

            byte[] buf = new byte[8192];
            int read;
            int xorIndex = 0;
            while ((read = bis.read(buf)) != -1) {
                if (shouldXor) {
                    for (int i = 0; i < read; i++) {
                        buf[i] = (byte)(buf[i] ^ segmentMask[xorIndex & 15]);
                        xorIndex++;
                    }
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
