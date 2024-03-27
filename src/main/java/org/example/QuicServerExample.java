package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class QuicServerExample {
    private static final InternalLogger LOGGER = InternalLoggerFactory.getInstance(QuicServerExample.class);


    public static void main(String[] args) throws Exception {
        int port = 443;
        int connectionIdLength = 20;
        String keyPath = QuicServerExample.class.getClassLoader().getResource("cert.key").getPath();
        String crtPath = QuicServerExample.class.getClassLoader().getResource("cert.crt").getPath();

        File keyFile = new File(keyPath);
        File certFile = new File(crtPath);

        QuicSslContext context = QuicSslContextBuilder.forServer(keyFile, null, certFile)
                .applicationProtocols("http/0.9")
                .earlyData(true).build();
        NioEventLoopGroup group = new NioEventLoopGroup(1);
        ChannelHandler codec = new QuicServerCodecBuilder()
                .sslContext(context)
                .localConnectionIdLength(connectionIdLength)
                .connectionIdAddressGenerator(FixedIPQuicConnectionIdGenerator.INSTANCE)
                .maxIdleTimeout(300000, TimeUnit.MILLISECONDS)
                .activeMigration(true)
                .initialMaxData(10000000)
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .initialMaxStreamDataBidirectionalRemote(1000000)
                .initialMaxStreamsBidirectional(100)
                .initialMaxStreamsUnidirectional(100)
                .tokenHandler(NoValidationQuicTokenHandler.INSTANCE)
                .handler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        QuicChannel channel = (QuicChannel) ctx.channel();
                        // Create streams etc..
                    }

                    public void channelInactive(ChannelHandlerContext ctx) {
                        ((QuicChannel) ctx.channel()).collectStats().addListener(f -> {
                            if (f.isSuccess()) {
                                LOGGER.info("Connection closed: {}", f.getNow());
                            }
                        });
                    }

                    @Override
                    public boolean isSharable() {
                        return true;
                    }

                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        System.err.println(System.currentTimeMillis() + " " + evt.getClass().getName());
                        super.userEventTriggered(ctx, evt);
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                        cause.printStackTrace();
                    }
                })
                .streamOption(ChannelOption.AUTO_READ, false)
                .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                    @Override
                    protected void initChannel(QuicStreamChannel ch) {
                        // Add a LineBasedFrameDecoder here as we just want to do some simple HTTP 0.9 handling.
                        ch.pipeline().addLast(new LineBasedFrameDecoder(1024))
                                .addLast(new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) {
                                        ByteBuf byteBuf = (ByteBuf) msg;
                                        try {
                                            ByteBuf buffer = ctx.alloc().directBuffer();
                                            buffer.writeCharSequence("Hello World!\r\n", CharsetUtil.US_ASCII);
                                            // Write the buffer and shutdown the output by writing a FIN.
                                            ctx.writeAndFlush(buffer);

                                        } finally {
                                            byteBuf.release();
                                        }
                                    }
                                });
                    }
                }).build();
        try {
            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast(new QuicAgentFrontendHandler(port, connectionIdLength));
                            ch.pipeline().addLast(codec);
                        }
                    })
                    .bind(new InetSocketAddress(port))
                    .sync().channel();
            System.out.println("ok");
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}