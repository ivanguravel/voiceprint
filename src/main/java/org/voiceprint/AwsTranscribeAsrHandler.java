package org.voiceprint;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.voiceprint.aws.TranscribeStreamingClientWrapper;

import java.io.IOException;
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

import java.util.Deque;

class AwsTranscribeAsrHandler extends ChannelInboundHandlerAdapter {

    private final Deque<String> deliveryAsrResultsQueue;
    private  AwsAsrDeliveryThread asrWorker;

    public AwsTranscribeAsrHandler() throws IOException {
        this.deliveryAsrResultsQueue = new ConcurrentLinkedDeque<>();
        this.asrWorker = new AwsAsrDeliveryThread(this.deliveryAsrResultsQueue);
        this.asrWorker.start();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws IOException {
        ByteBuf buffer = (ByteBuf) msg;
        try {
            ByteBuffer byteBuffer = toNioBuffer(buffer);

            byte[] byteArray = new byte[byteBuffer.remaining()];
            byteBuffer.get(byteArray);

            // send audio for recognition.
            this.asrWorker.addMessage(byteArray);

            // deliver the text after ASR to further topics categorization.
            while (!deliveryAsrResultsQueue.isEmpty()) {
                String words = deliveryAsrResultsQueue.poll();
                ctx.fireChannelRead(words);
            }
        } finally {
            buffer.release();
        }
    }


    private static class AwsAsrDeliveryThread extends Thread {

        private final PipedInputStream asrInputStream;
        private final PipedOutputStream outputStream;
        private final Deque<String> deliveryAsrResultsQueue;

        private TranscribeStreamingClientWrapper asrClient;


        public AwsAsrDeliveryThread(Deque<String> deliveryAsrResultsQueue) throws IOException {
            this.asrInputStream = new PipedInputStream();
            this.outputStream = new PipedOutputStream(this.asrInputStream);
            this.deliveryAsrResultsQueue = deliveryAsrResultsQueue;
            this.asrClient = new TranscribeStreamingClientWrapper(getClient(), this.deliveryAsrResultsQueue);
        }

        @Override
        public void run() {
            this.asrClient.transcribe(this.asrInputStream);
        }

        public void addMessage(byte[] message) throws IOException {
            this.outputStream.write(message);
        }
    }

    public static software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient getClient() {
        Region region = getRegion();
        String endpoint = "https://transcribestreaming." + region.toString().toLowerCase().replace('_','-') + ".amazonaws.com";
        try {
            return software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient.builder()
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
