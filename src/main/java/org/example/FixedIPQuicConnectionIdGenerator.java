package org.example;

import io.netty.incubator.codec.quic.QuicConnectionIdGenerator;
import io.netty.util.internal.ObjectUtil;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class FixedIPQuicConnectionIdGenerator implements QuicConnectionIdGenerator {
    public static final QuicConnectionIdGenerator INSTANCE = new FixedIPQuicConnectionIdGenerator();

    private static final ThreadLocal<MessageDigest> DIGEST_THREAD_LOCAL = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not supported", e);
        }
    });

    public static byte[] newId(byte[] id, String ip, int outLength) {
        MessageDigest messageDigest = DIGEST_THREAD_LOCAL.get();
        byte[] hashCode = messageDigest.digest(id);
        String[] localIp = ip.split("\\.");
        int part1 = Integer.parseInt(localIp[0]);
        int part2 = Integer.parseInt(localIp[1]);
        int part3 = Integer.parseInt(localIp[2]);
        int part4 = Integer.parseInt(localIp[3]);
        hashCode[outLength - 8] = (byte) (part1 ^ hashCode[0]);
        hashCode[outLength - 7] = (byte) (part2 ^ hashCode[1]);
        hashCode[outLength - 6] = (byte) (part3 ^ hashCode[2]);
        hashCode[outLength - 5] = (byte) (part4 ^ hashCode[3]);
        hashCode[outLength - 4] = (byte) (part1 ^ hashCode[4]);
        hashCode[outLength - 3] = (byte) (part2 ^ hashCode[5]);
        hashCode[outLength - 2] = (byte) (part3 ^ hashCode[6]);
        hashCode[outLength - 1] = (byte) (part4 ^ hashCode[7]);

        return Arrays.copyOf(hashCode, outLength);
    }

    public static String parseIp(byte[] id) {
        if (id == null || id.length < 8) {
            return null;
        }
        int part1 = (id[id.length - 8] ^ id[0]) & 0xff;
        int part2 = (id[id.length - 7] ^ id[1]) & 0xff;
        int part3 = (id[id.length - 6] ^ id[2]) & 0xff;
        int part4 = (id[id.length - 5] ^ id[3]) & 0xff;
        int part1_ = (id[id.length - 4] ^ id[4]) & 0xff;
        int part2_ = (id[id.length - 3] ^ id[5]) & 0xff;
        int part3_ = (id[id.length - 2] ^ id[6]) & 0xff;
        int part4_ = (id[id.length - 1] ^ id[7]) & 0xff;

        if (part1 != part1_ || part2 != part2_ || part3 != part3_ || part4 != part4_) {
            return null;
        }

        String ip = part1 + "." + part2 + "." + part3 + "." + part4;
        return ip;
    }

    @Override
    public ByteBuffer newId(int i) {
        throw new UnsupportedOperationException("FixedIPQuicConnectionIdGenerator should always have an input to sign with");
    }

    @Override
    public ByteBuffer newId(ByteBuffer buffer, int length) {
        ObjectUtil.checkNotNull(buffer, "buffer");
        ObjectUtil.checkPositive(buffer.remaining(), "buffer");
        ObjectUtil.checkInRange(length, 0, this.maxConnectionIdLength(), "length");
        byte[] signBytes = new byte[buffer.remaining()];
        buffer.get(signBytes, 0, signBytes.length);

        try {
            signBytes = newId(signBytes, InetAddress.getLocalHost().getHostAddress(), length);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }

        return ByteBuffer.wrap(signBytes);
    }

    @Override
    public int maxConnectionIdLength() {
        return QuicConnectionIdGenerator.signGenerator().maxConnectionIdLength();
    }

    @Override
    public boolean isIdempotent() {
        return true;
    }
}
