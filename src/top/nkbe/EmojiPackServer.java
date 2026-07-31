package top.nkbe;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Enumeration;

/**
 * 內建迷你 HTTP server：提供內嵌的 emoji 資源包給 Java 端玩家下載。
 * 只服務單一檔案 /resourcepack.zip，無檔案存取。
 */
public class EmojiPackServer {

    private final JavaPlugin plugin;
    private final byte[] packBytes;
    private final byte[] packHash;
    private final int port;
    private final String host;
    private ServerSocket server;

    public EmojiPackServer(JavaPlugin plugin, byte[] packBytes, int port, String host) {
        this.plugin = plugin;
        this.packBytes = packBytes;
        this.packHash = sha1(packBytes);
        this.port = port;
        this.host = host;
    }

    public void start() throws IOException {
        server = new ServerSocket(port);
        Thread thread = new Thread(this::acceptLoop, "MoveLights-EmojiPack");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        if (server != null) {
            try {
                server.close();
            } catch (IOException ignored) {
            }
            server = null;
        }
    }

    public String getUrl() {
        String h = host;
        if (h == null || h.isEmpty()) {
            h = Bukkit.getIp(); // server.properties 的 server-ip
        }
        if (h == null || h.isEmpty()) {
            h = findLanIp();
        }
        if (h == null || h.isEmpty()) {
            h = "localhost";
            plugin.getLogger().warning("無法自動偵測伺服器位址，請在 config.yml 設定 emoji-pack.host");
        }
        return "http://" + h + ":" + port + "/resourcepack.zip";
    }

    public byte[] getHash() {
        return packHash;
    }

    // 找一個非 loopback 的 IPv4 位址（公網/NAT 環境建議直接在 config 填 emoji-pack.host）
    private static String findLanIp() {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface net = nets.nextElement();
                if (!net.isUp() || net.isLoopback()) continue;
                Enumeration<InetAddress> addrs = net.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private void acceptLoop() {
        while (server != null && !server.isClosed()) {
            try {
                Socket socket = server.accept();
                Thread t = new Thread(() -> handle(socket), "MoveLights-EmojiPack");
                t.setDaemon(true);
                t.start();
            } catch (IOException ignored) {
                // server 已關閉
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            String requestLine = reader.readLine();
            if (requestLine == null) return;

            String[] parts = requestLine.split(" ");
            if (parts.length < 2 || !"GET".equalsIgnoreCase(parts[0])) {
                out.write(("HTTP/1.1 405 Method Not Allowed\r\n"
                        + "Content-Length: 0\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                out.flush();
                return;
            }

            // 忽略其餘 header
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // ignore headers
            }

            out.write(("HTTP/1.1 200 OK\r\n"
                    + "Content-Type: application/zip\r\n"
                    + "Content-Length: " + packBytes.length + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(packBytes);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (Exception e) {
            return null;
        }
    }
}
