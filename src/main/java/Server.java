import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final ExecutorService executorService;
    private final Map<String, Map<String, Handler>> handlers;

    private final Handler notFoundHandler = (request, out) -> {
        try {
            out.write((
                    "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    public Server(int threadPoolSize) {
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        this.handlers = new ConcurrentHashMap<>();
    }

    public void addHandler(String method, String path, Handler handler) {
        Optional.ofNullable(handlers.get(method))
                .ifPresentOrElse(pathHandlerMap -> pathHandlerMap.put(path, handler),
                        () -> handlers.put(method, new ConcurrentHashMap<>(Map.of(path, handler))));
    }

    public void listen(int port) {
        try (final var serverSocket = new ServerSocket(port)) {
            while (true) {
                final var socket = serverSocket.accept();
                executorService.submit(() -> process(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void process(Socket socket) {
        try (socket;
             final var in = socket.getInputStream();
             final var out = new BufferedOutputStream(socket.getOutputStream());) {

            var request = Request.fromInputStream(in);

            Optional.ofNullable(handlers.get(request.getMethod()))
                    .map(pathHandlerMap -> pathHandlerMap.get(request.getPath()))
                    .ifPresentOrElse(handler -> {
                                try {
                                    handler.handle(request, out);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            },
                            () -> {
                                try {
                                    notFoundHandler.handle(request, out);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });

        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
