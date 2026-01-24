package com.example.chillgram.common.logging;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * 요청 바디를 "앞 N바이트만" 복사하여 저장하는 컴포넌트.
 *
 * 구현 방식:
 * - request.getBody() Flux<DataBuffer>를 그대로 흘려보내되(doOnNext),
 *   DataBuffer의 내용을 복사하여 별도 버퍼(ByteArraySink)에 축적한다.
 * - 원본 DataBuffer는 소비하지 않으므로 컨트롤러의 @RequestBody 처리가 깨지지 않는다.
 *
 * 저장 위치:
 * - exchange attributes에 request-scope로 저장하여,
 *   요청 처리 종료 시점(WebFilter doFinally)에서 로그에 활용한다.
 */
@Component
public class RequestBodyCaptor {

    public static final String ATTR_REQ_BODY_SINK = "cachedReqBodySink";
    public static final String ATTR_REQ_BODY_TRUNC = "cachedReqBodyTruncated";

    private final RequestBodyCapturePolicy policy;

    public RequestBodyCaptor(RequestBodyCapturePolicy policy) {
        this.policy = policy;
    }

    public ServerWebExchange decorateIfNeeded(ServerWebExchange exchange) {
        var req = exchange.getRequest();

        if (!policy.shouldCapture(req.getMethod(), req.getHeaders())) {
            return exchange;
        }

        var sink = new ByteArraySink(policy.maxBytes());
        exchange.getAttributes().put(ATTR_REQ_BODY_SINK, sink);
        exchange.getAttributes().put(ATTR_REQ_BODY_TRUNC, Boolean.FALSE);

        var decorated = new ServerHttpRequestDecorator(req) {
            @Override
            public Flux<DataBuffer> getBody() {
                return super.getBody().doOnNext(buf -> tap(exchange, buf, sink));
            }
        };

        return exchange.mutate().request(decorated).build();
    }

    private void tap(ServerWebExchange exchange, DataBuffer dataBuffer, ByteArraySink sink) {
        if (sink.isFull()) return;

        ByteBuffer bb = dataBuffer.asByteBuffer().asReadOnlyBuffer();
        int canRead = Math.min(bb.remaining(), sink.remaining());
        if (canRead <= 0) return;

        byte[] chunk = new byte[canRead];
        bb.get(chunk);
        sink.write(chunk);

        if (sink.isFull() && bb.remaining() > 0) {
            exchange.getAttributes().put(ATTR_REQ_BODY_TRUNC, Boolean.TRUE);
        }
    }

    public String readCapturedBody(ServerWebExchange exchange) {
        Object o = exchange.getAttribute(ATTR_REQ_BODY_SINK);
        if (!(o instanceof ByteArraySink sink)) return null;

        String body = new String(sink.toByteArray(), StandardCharsets.UTF_8);
        Boolean trunc = exchange.getAttribute(ATTR_REQ_BODY_TRUNC);
        return Boolean.TRUE.equals(trunc) ? body + "...(truncated)" : body;
    }

    public static final class ByteArraySink {
        private final byte[] buf;
        private int pos;

        public ByteArraySink(int maxBytes) {
            this.buf = new byte[maxBytes];
        }

        public int remaining() { return buf.length - pos; }
        public boolean isFull() { return pos >= buf.length; }

        public void write(byte[] src) {
            int len = Math.min(src.length, remaining());
            if (len <= 0) return;
            System.arraycopy(src, 0, buf, pos, len);
            pos += len;
        }

        public byte[] toByteArray() {
            byte[] out = new byte[pos];
            System.arraycopy(buf, 0, out, 0, pos);
            return out;
        }
    }
}