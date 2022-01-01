package org.voiceprint;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionConfig;
import io.netty.bootstrap.ServerBootstrap;

import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;

public class Server {

    private static final String CREDENTIALS_LOCATION = "ASR_CREDENTIALS_LOCATION";

    public static void main(String[] args) throws Exception {
        System.out.println("netty as a service preparation");
        EventLoopGroup master = new NioEventLoopGroup();
        EventLoopGroup slave = new NioEventLoopGroup();
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(master, slave);
            serverBootstrap.channel(NioServerSocketChannel.class);
            serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel inboundChannel) {
                    System.out.println("catch the data");
                    inboundChannel.pipeline().addLast(
                            new Asr(createRecognitionConfig(), createAsrCredentials()),
                            new Printer());
                }
            });

            Channel channel = serverBootstrap.bind(8_081).sync().channel();
            System.out.println("server started");
            channel.closeFuture().sync();

        } finally {
            slave.shutdownGracefully();
            master.shutdownGracefully();
        }
    }

    private static RecognitionConfig createRecognitionConfig() {
        return RecognitionConfig.newBuilder()
                .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                .setLanguageCode("en-US")
                .setSampleRateHertz(16_000)
                .build();
    }

    private static GoogleCredentials createAsrCredentials() {
        String credentialsLocationValue = System.getenv(CREDENTIALS_LOCATION);
        try (FileInputStream file = new FileInputStream(credentialsLocationValue); InputStream inputStream = new BufferedInputStream(file)) {
            return GoogleCredentials.fromStream(inputStream);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
