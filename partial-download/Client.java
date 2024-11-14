import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class Client {
    private static Map<String, Long> process = new HashMap<>();
    private static final long partialRequest = 1024 * 1024; // 1MB

    public static void main(String[] args) {
        // each file
        int maxRetries = 10;
        Duration duration = Duration.ofMinutes(1);
        String filename = "video-client.mp4";
        process.put(filename, 0L);

        // process download
        int retries = 1;
        while (retries <= maxRetries) {
            int respCode;
            do {
                respCode = downloadWithChannel(filename);
                System.out.println("download partial");
                System.out.println("response code: " + respCode);
            } while (respCode == HttpURLConnection.HTTP_PARTIAL);

            if (respCode == HttpURLConnection.HTTP_OK) {
                process.remove(filename);
                break;
            }

            retries++;
            try {
                Thread.sleep(duration.multipliedBy(retries).toMillis());
            } catch (InterruptedException e) {
                System.out.println("Interrupted while waiting for retry download");
            }
        }

        System.out.println("download finish. failed tracker: " + process.toString());
        /*
        try {
            Files.delete(Path.of(filename));
        } catch (IOException e) {
            System.out.println("delete file failed: " + filename);
        }
        */
    }

    private static int downloadWithChannel(String filename) {
        File file = new File(filename);
        long downoladedLength = 0;
        if (file.exists()) {
            downoladedLength = file.length();
        }
        long rangeEnd = downoladedLength + partialRequest;
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) new URL("http://localhost:6969").openConnection();
            connection.setRequestProperty("Range", "bytes=" + downoladedLength + "-" + rangeEnd);

            int respCode = connection.getResponseCode();
            if (respCode == HttpURLConnection.HTTP_PARTIAL || respCode == HttpURLConnection.HTTP_OK) {
                try (ReadableByteChannel rbc = Channels.newChannel(connection.getInputStream());
                     RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                    raf.seek(downoladedLength);
                    raf.getChannel().transferFrom(rbc, downoladedLength, Long.MAX_VALUE);
                } catch (IOException e) {
                    System.out.println("partial err: " + e.getMessage());
                    return 500;
                }
            }

            return respCode;
        } catch (IOException e) {
            System.out.println("try to connect: " + e.getMessage());
            return 500;
        }
    }
}