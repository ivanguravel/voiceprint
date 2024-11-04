package org.voiceprint;

import io.netty.bootstrap.ServerBootstrap;

import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.io.IOException;


public class Server {


    public static void main(String[] args) throws Exception {
        System.out.println("netty as a service preparation");
        EventLoopGroup main = new NioEventLoopGroup(
                Thread.ofPlatform()
                        .name("voiceprint-event-main-loop").factory());
        EventLoopGroup worker = new NioEventLoopGroup(Thread.ofPlatform()
                .name("voiceprint-event-worker-loop").factory());
        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(main, worker);
            serverBootstrap.channel(NioServerSocketChannel.class);
            serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel inboundChannel) throws IOException {
                    System.out.println("catch the audio data and start categorization process .... ");
                    inboundChannel.pipeline().addLast(
                            new AwsTranscribeAsrHandler(),
                            new ContentCategorizerHandler(),
                            new PrintHandler());
                }
            });

            Channel channel = serverBootstrap.bind(8_081).sync().channel();
            System.out.println("server started");
            channel.closeFuture().sync();

        } finally {
            worker.shutdownGracefully();
            main.shutdownGracefully();
        }
    }
}
