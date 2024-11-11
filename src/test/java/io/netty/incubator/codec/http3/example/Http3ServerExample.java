/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.incubator.codec.http3.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.http3.*;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.lang.Integer.parseInt;

/**
 * https://www.chromium.org/quic/playing-with-quic/
 *
 *
 * /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --user-data-dir=/tmp/chrome-profile \
 *   --no-proxy-server \
 *   --enable-quic \
 *   --origin-to-force-quic-on=programmer-yumi.top:443 \
 *   --host-resolver-rules='MAP programmer-yumi.top:443 127.0.0.1:9999' \
 *   https://programmer-yumi.top
 */

public final class Http3ServerExample {

    private static final String LATENCY_FIELD_NAME = "latency";
    private static final int MIN_LATENCY = 0;
    private static final int MAX_LATENCY = 1000;
    private static final String IMAGE_COORDINATE_Y = "y";
    private static final String IMAGE_COORDINATE_X = "x";

    private static final byte[] CONTENT = "Hello World!\r\n".getBytes(CharsetUtil.US_ASCII);
    static final int PORT = 9999;

    private Http3ServerExample() { }

    public static void main(String... args) throws Exception {
        int port;
        // Allow to pass in the port so we can also use it to run h3spec against
        if (args.length == 1) {
            port = Integer.parseInt(args[0]);
        } else {
            port = PORT;
        }
        NioEventLoopGroup group = new NioEventLoopGroup(1);

        String path = Http3ServerExample.class.getClassLoader().getResource("").getPath();
        File keyFile = new File(path + "cert.key");
        File certFile = new File(path + "cert.crt");



        SelfSignedCertificate cert = new SelfSignedCertificate();
        QuicSslContext sslContext = QuicSslContextBuilder.forServer(keyFile, null, certFile)
                .applicationProtocols(Http3.supportedApplicationProtocols()).build();
        ChannelHandler codec = Http3.newQuicServerCodecBuilder()
                .sslContext(sslContext)
                .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                .handler(new ChannelInitializer<QuicChannel>() {
                    @Override
                    protected void initChannel(QuicChannel ch) {
                        // Called for each connection
                        ch.pipeline().addLast(new Http3ServerConnectionHandler(
                                new ChannelInitializer<QuicStreamChannel>() {
                                    // Called for each request-stream,
                                    @Override
                                    protected void initChannel(QuicStreamChannel ch) {
                                        ch.pipeline().addLast(new Http3RequestStreamInboundHandler() {

                                            @Override
                                            protected void channelRead(ChannelHandlerContext ctx,
                                                                       Http3HeadersFrame frame, boolean isLast) {
                                                if (isLast) {
                                                    QueryStringDecoder queryString = new QueryStringDecoder(frame.headers().path().toString());
                                                    int latency = toInt(firstValue(queryString, LATENCY_FIELD_NAME), 0);
                                                    if (latency < MIN_LATENCY || latency > MAX_LATENCY) {
                                                        writeBadResponse(ctx);
                                                        return;
                                                    }
                                                    String x = firstValue(queryString, IMAGE_COORDINATE_X);
                                                    String y = firstValue(queryString, IMAGE_COORDINATE_Y);
                                                    if (x == null || y == null) {
                                                        handlePage(ctx, latency);
                                                    } else {
                                                        handleImage(x, y, ctx, latency);
                                                    }
                                                }
                                                ReferenceCountUtil.release(frame);
                                            }

                                            @Override
                                            protected void channelRead(ChannelHandlerContext ctx,
                                                                       Http3DataFrame frame, boolean isLast) {
                                                System.out.println("***");
                                                if (isLast) {
                                                    System.out.println("///");
                                                    writeResponse(ctx);
                                                }
                                                ReferenceCountUtil.release(frame);
                                            }


                                            private void handlePage(ChannelHandlerContext ctx, int latency) {
                                                byte[] body = Html.body(latency);
                                                ByteBuf content = ctx.alloc().buffer(Html.HEADER.length + body.length + Html.FOOTER.length);
                                                content.writeBytes(Html.HEADER);
                                                content.writeBytes(body);
                                                content.writeBytes(Html.FOOTER);

                                                Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                                                headersFrame.headers().status("200");
                                                headersFrame.headers().add("server", "netty");
                                                headersFrame.headers().add(CONTENT_TYPE, "text/html; charset=UTF-8");
                                                headersFrame.headers().addInt("content-length", content.readableBytes());
                                                ctx.write(headersFrame);
                                                ctx.writeAndFlush(new DefaultHttp3DataFrame(
                                                                Unpooled.wrappedBuffer(content)))
                                                        .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                            }

                                            private void handleImage(String x, String y, ChannelHandlerContext ctx, int latency) {
                                                ByteBuf image = ImageCache.INSTANCE.image(parseInt(x), parseInt(y));

                                                Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                                                headersFrame.headers().status("200");
                                                headersFrame.headers().add("server", "netty");
                                                headersFrame.headers().add(CONTENT_TYPE, "image/jpeg");
                                                headersFrame.headers().addInt("content-length", image.readableBytes());

                                                ctx.executor().schedule(() -> {
                                                    ctx.write(headersFrame);
                                                    ctx.writeAndFlush(new DefaultHttp3DataFrame(
                                                                    Unpooled.wrappedBuffer(image)))
                                                            .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                                }, latency, TimeUnit.MILLISECONDS);
                                            }


                                            private void writeResponse(ChannelHandlerContext ctx) {
                                                Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                                                headersFrame.headers().status("200");
                                                headersFrame.headers().add("server", "netty");
                                                headersFrame.headers().addInt("content-length", CONTENT.length);
                                                ctx.write(headersFrame);
                                                ctx.writeAndFlush(new DefaultHttp3DataFrame(
                                                        Unpooled.wrappedBuffer(CONTENT)))
                                                        .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
                                            }

                                            private void writeBadResponse(ChannelHandlerContext ctx) {
                                                Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
                                                headersFrame.headers().status("400");
                                                headersFrame.headers().add("server", "netty");
                                                ctx.writeAndFlush(headersFrame);
                                            }

                                            private int toInt(String string, int defaultValue) {
                                                if (string != null && !string.isEmpty()) {
                                                    return Integer.parseInt(string);
                                                }
                                                return defaultValue;
                                            }

                                            private String firstValue(QueryStringDecoder query, String key) {
                                                checkNotNull(query, "Query can't be null!");
                                                List<String> values = query.parameters().get(key);
                                                if (values == null || values.isEmpty()) {
                                                    return null;
                                                }
                                                return values.get(0);
                                            }
                                        });
                                    }
                                }));
                    }
                }).build();
        try {
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(new InetSocketAddress(port)).sync().channel();
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
