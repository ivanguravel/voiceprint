package org.voiceprint;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.voiceprint.aws.TranscribeStreamingSynchronousClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.*;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;


import java.util.Deque;

class AwsTranscribeAsr extends ChannelInboundHandlerAdapter {

    private final Deque<String> results4delivery;
    private PipedInputStream voice4recognition;
    private PipedOutputStream outputStream;

    private  AwsAsrDeliveryThread client;

    public AwsTranscribeAsr() throws IOException {
        this.results4delivery = new ConcurrentLinkedDeque<>();
        this.voice4recognition = new PipedInputStream();
        outputStream = new PipedOutputStream(this.voice4recognition);
        this.client = new AwsAsrDeliveryThread(this.voice4recognition, this.results4delivery);
        this.client.start();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws IOException {
        ByteBuf buffer = (ByteBuf) msg;
        try {
            ByteBuffer byteBuffer = toNioBuffer(buffer);

            byte[] byteArray = new byte[byteBuffer.remaining()];
            byteBuffer.get(byteArray);

            // send audio for recognition.
            this.outputStream.write(byteArray);

            // deliver the text after ASR to further topics categorization.
            while (!results4delivery.isEmpty()) {
                String words = results4delivery.poll();
                ctx.fireChannelRead(words);
            }
        } finally {
            buffer.release();
        }
    }


    private static class AwsAsrDeliveryThread extends Thread {

        private final InputStream inputStream;
        private final Deque<String> results4delivery;

        private  TranscribeStreamingSynchronousClient synchronousClient;


        public AwsAsrDeliveryThread(InputStream inputStream,
                                    Deque<String> results4delivery) {

            this.inputStream = inputStream;
            this.results4delivery = results4delivery;
            this.synchronousClient = new TranscribeStreamingSynchronousClient(getClient(), this.results4delivery);
        }

        @Override
        public void run() {
            this.synchronousClient.transcribe(this.inputStream);
        }
    }

    public static TranscribeStreamingAsyncClient getClient() {
        Region region = getRegion();
        String endpoint = "https://transcribestreaming." + region.toString().toLowerCase().replace('_','-') + ".amazonaws.com";
        try {
            return TranscribeStreamingAsyncClient.builder()
                    .credentialsProvider(getCredentials())
                    .endpointOverride(new URI(endpoint))
                    .region(region)
                    .build();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI syntax for endpoint: " + endpoint);
        }

    }

    private static Region getRegion() {
        Region region;
        try {
            region = new DefaultAwsRegionProviderChain().getRegion();
        } catch (SdkClientException e) {
            region = Region.US_WEST_2;
        }
        return region;
    }


    private static AwsCredentialsProvider getCredentials() {
        return DefaultCredentialsProvider.create();
    }

    public static ByteBuffer toNioBuffer(ByteBuf buffer) {
        if (buffer.isDirect()) {
            return buffer.nioBuffer();
        }
        final byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(buffer.readerIndex(), bytes);
        return ByteBuffer.wrap(bytes);
    }
}
