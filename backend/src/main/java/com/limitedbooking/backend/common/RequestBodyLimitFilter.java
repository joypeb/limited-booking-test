package com.limitedbooking.backend.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Component
public class RequestBodyLimitFilter extends OncePerRequestFilter {
    public static final int MAX_BODY_BYTES = 8192;

    @Override
    @NullMarked
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > MAX_BODY_BYTES) {
            rejectPayloadTooLarge(request, response);
            return;
        }

        byte[] cachedBytes = readUpToLimit(request.getInputStream());
        if (cachedBytes == null) {
            rejectPayloadTooLarge(request, response);
            return;
        }

        filterChain.doFilter(new CachedBodyHttpServletRequest(request, cachedBytes), response);
    }

    public static byte[] getCachedBody(HttpServletRequest request) {
        HttpServletRequest current = request;
        while (current != null) {
            if (current instanceof CachedBodyHttpServletRequest cached) {
                return cached.cachedBody();
            }
            if (current instanceof HttpServletRequestWrapper wrapper
                    && wrapper.getRequest() instanceof HttpServletRequest inner
                    && inner != current) {
                current = inner;
            } else {
                break;
            }
        }
        return new byte[0];
    }

    private static byte[] readUpToLimit(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return new byte[0];
        }
        var out = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int totalRead = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            totalRead += bytesRead;
            if (totalRead > MAX_BODY_BYTES) {
                return null;
            }
            out.write(buffer, 0, bytesRead);
        }
        return out.toByteArray();
    }

    private static void rejectPayloadTooLarge(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiExceptionHandler.writeProblem(
                request,
                response,
                HttpStatus.CONTENT_TOO_LARGE,
                "요청 본문이 너무 큽니다",
                "요청 본문은 8,192바이트(8KiB)를 초과할 수 없습니다.",
                "PAYLOAD_TOO_LARGE"
        );
    }

    public static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody == null ? new byte[0] : cachedBody.clone();
        }

        public byte[] cachedBody() {
            return cachedBody.clone();
        }

        @Override
        public int getContentLength() {
            return cachedBody.length;
        }

        @Override
        public long getContentLengthLong() {
            return cachedBody.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            var byteStream = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return byteStream.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException("비동기 ReadListener는 지원하지 않습니다.");
                }

                @Override
                public int read() {
                    return byteStream.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return byteStream.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = (encoding == null || encoding.isBlank())
                    ? StandardCharsets.UTF_8
                    : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
