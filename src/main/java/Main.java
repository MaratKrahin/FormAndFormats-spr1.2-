import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        final var server = new Server(64);
        server.addHandler("POST", "/someFile1.html", (request, out) -> {
            var filePath = Path.of(".", "someDir", request.getPath());
            var mimeType = Files.probeContentType(filePath);
            var length = Files.size(filePath);
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length" + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();
        });
        server.addHandler("GET", "/someFile2.html", (request, out) -> {
        });
        server.listen(8080);
    }
}

