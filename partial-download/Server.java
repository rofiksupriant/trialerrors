

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.util.List;

public class Server {

    public static void main(String[] args) {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(6969), 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        server.createContext("/", exchange -> {
            Headers requestHeaders = exchange.getRequestHeaders();
            boolean range = requestHeaders.containsKey("Range");

            OutputStream responseBody = exchange.getResponseBody();

            File file = new File("video-server.mp4");
            if (!file.exists()) {
                String response = "file not found";
                exchange.sendResponseHeaders(500, response.length());
                responseBody.write(response.getBytes());
                exchange.close();
            }
            
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {

                if (range) {
                    // Handle Range requests
                    List<String> rangeList = requestHeaders.get("Range");
                    System.out.println("rangeList: " + rangeList);
                    String rangeValue = rangeList.get(0).substring("bytes=".length());
                    String[] ranges = rangeValue.split("-");
                    long rangeStart = Long.parseLong(ranges[0]);
                    long rangeEnd = ranges.length > 1 ? Long.parseLong(ranges[1]) : 8192;

                    long contentLengthReq = rangeEnd - rangeStart + 1;
                    long totalServedLength = rangeStart + rangeEnd - rangeStart;

                    System.out.println("totalServedLength: " + totalServedLength);

                    // Write the requested range of the file to the response
                    raf.seek(rangeStart);
                    byte[] buffer = new byte[Math.toIntExact(contentLengthReq)];
                    long bytesRemaining = contentLengthReq;
                    int bytesRead;
                    int bytesReaded = 0;
                    while (bytesRemaining > 0 && (bytesRead = raf.read(buffer, bytesReaded, (int) Math.min(buffer.length, bytesRemaining))) != -1) {
                        bytesRemaining -= bytesRead;
                        bytesReaded += bytesRead;
                    }

                    // Set response headers for partial content
                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    exchange.getResponseHeaders().set("Content-Range", "bytes " + rangeStart + "-" + rangeEnd + "/" + file.length());
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(contentLengthReq));
                    exchange.sendResponseHeaders(totalServedLength >= file.length() ? 200 : 206, bytesReaded);

                    responseBody.write(buffer, 0, bytesReaded);
                } else {
                    // Handle full file download
                    long fileLength = file.length();

                    // Set response headers for full content
                    exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
                    exchange.getResponseHeaders().set("Content-Length", String.valueOf(fileLength));
                    exchange.sendResponseHeaders(200, fileLength);

                    // Write the full file content to the response
                    byte[] buffer = new byte[1024*1024];
                    int bytesRead;
                    while ((bytesRead = raf.read(buffer)) != -1) {
                        responseBody.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
                exchange.sendResponseHeaders(500, -1);
            } finally {
                // impersonate slow network
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                exchange.close();
            }
        });

        server.setExecutor(null);
        server.start();
    }
}
