package org.example;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class QuicAgentFrontendHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private final int port;
    private final int connectionIdLength;

    public QuicAgentFrontendHandler(int port, int connectionIdLength) {
        this.port = port;
        this.connectionIdLength = connectionIdLength;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws UnknownHostException {
        ByteBuf byteBuf = packet.content();
        byteBuf.markReaderIndex();
        byte status = byteBuf.readByte();
        boolean isLongHeader = (status & 0x80) == 0x80;
        byte[] dcid;

        if (isLongHeader) {
            byteBuf.readInt();
            int len = byteBuf.readByte();
            if (len != connectionIdLength) {
                byteBuf.resetReaderIndex();
                ctx.fireChannelRead(packet.retain());
                return;
            }
            dcid = new byte[len];
        } else {
            dcid = new byte[connectionIdLength];
        }
        byteBuf.readBytes(dcid);
        String ip = FixedIPQuicConnectionIdGenerator.parseIp(dcid);
        if (ip == null || ip.equals(InetAddress.getLocalHost().getHostAddress())) {
            byteBuf.resetReaderIndex();
            ctx.fireChannelRead(packet.retain());
            return;
        }
        byteBuf.resetReaderIndex();
        byte[] data = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(data);
        InetSocketAddress targetServer = new InetSocketAddress(ip, port);
        //AgentClientManager.sendPacket(targetServer, dcid, data, packet.sender(), ctx.channel(), packet.recipient());
    }
}