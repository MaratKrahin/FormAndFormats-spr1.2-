import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Data
@AllArgsConstructor
public class Request {
    private final static int readAheadLimit = 4096;
    private final static String GET = "GET";
    private final static String POST = "POST";

    private final String method;
    private final String urlPath;
    private final String path;
    private final Map<String, String> queryString;
    private final Map<String, String> headers;
    private final String msgBody;

    public static Request fromInputStream(InputStream in) throws IOException, URISyntaxException {
        in.mark(readAheadLimit);
        byte[] buffer = new byte[readAheadLimit];
        int read = in.read(buffer);

        byte[] requestLineDelimiter = new byte[]{'\r', '\n'};
        int requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);

        if (requestLineEnd == -1) {
            throw new IOException("Bad request");
        }

        String[] requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
        if (requestLine.length != 3) {
            throw new IOException("Bad request");
        }

        String method = requestLine[0];

        String urlPath = requestLine[1];

        if (!urlPath.startsWith("/")) {
            throw new IOException("Bad request");
        }

        Map<String, String> querryString = getQueryParams(urlPath);

        String path = urlPath.substring(0, urlPath.indexOf("&"));

        byte[] headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
        int headersStart = requestLineEnd + requestLineDelimiter.length;
        int headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);

        if (headersEnd == -1) {
            throw new IOException("Bad request");
        }

        in.reset();
        in.skip(headersStart);

        byte[] headersBytes = in.readNBytes(headersEnd - headersStart);

        List<String> listOfHeaders = Arrays.asList(new String(headersBytes).split("\r\n"));
        Map<String, String> headers = new HashMap<>();

        listOfHeaders.forEach(headerLine -> {
            var i = headerLine.indexOf(":");
            var headerName = headerLine.substring(0, i);
            var headerValue = headerLine.substring(i + 2);
            headers.put(headerName, headerValue);
        });

        String body = "";
        if (!method.equals(GET)) {
            in.skip(headersDelimiter.length);

            Optional<String> contentLength = extractHeader(headers, "Content-Length");

            if (contentLength.isPresent()) {
                int length = Integer.parseInt(contentLength.get());
                byte[] bodyBytes = in.readNBytes(length);
                body = new String(bodyBytes);
            }
        }
        return new Request(method, urlPath, path, querryString, headers, body);
    }

    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        cont:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue cont;
                }
            }
            return i;
        }
        return -1;
    }

    private static Optional<String> extractHeader(Map<String, String> headers, String header) {
        return headers.keySet().stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    public static Map<String, String> getQueryParams(String url) throws URISyntaxException {
        List<NameValuePair> params = URLEncodedUtils.parse(new URI(url), String.valueOf(StandardCharsets.UTF_8));

        Map<String, String> queryParams = new HashMap<>();

        for (NameValuePair param : params) {
            queryParams.put(param.getName(), param.getValue());
        }

        return queryParams;
    }

}
