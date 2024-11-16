package org.voiceprint;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.ahocorasick.trie.PayloadTrie;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class ContentCategorizerHandler extends ChannelInboundHandlerAdapter {

    private final Map<String, PayloadTrie<String>> categories;
    LinkedBlockingDeque<String> deliveryAsrResultsQueue = new LinkedBlockingDeque<>();
    Deque<String> deliveryCategorizationResultsQueue = new ConcurrentLinkedDeque<>();

    private final Thread categorizationWorker;

    public ContentCategorizerHandler() {

        this.categories = Map.of(
                "Amazon", buildTrieFromWords(
                        "Amazon",
                        "AWS",
                        "Lambda",
                        "ec2",
                        "ECS",
                        "elastic container registry",
                        "elastic load balancing"
                ),
                "General Tech Things", buildTrieFromWords(
                        "Docker",
                        "WordPress",
                        "Platform"
                ));

        this.categorizationWorker = new Thread(new ContentCategorizationWorker());
        this.categorizationWorker.start();
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        deliveryAsrResultsQueue.add(msg.toString());
        while (!deliveryCategorizationResultsQueue.isEmpty()) {
            ctx.fireChannelRead(deliveryCategorizationResultsQueue.poll());
        }
    }


    private static PayloadTrie<String> buildTrieFromWords(String... words) {
        PayloadTrie.PayloadTrieBuilder<String> builder =
                PayloadTrie.<String>builder()
                        .onlyWholeWords()
                        .ignoreCase();

        for (String word : words) {
            builder.addKeyword(word);
        }

        return builder.build();
    }


    class ContentCategorizationWorker implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                String message = safeTakeFromQueue();
                if (message == null) {
                    break;
                }
                Set<String> topics = categories.keySet();
                for (String topic : topics) {
                    PayloadTrie<String> trie = categories.get(topic);
                    if (!trie.parseText(message).isEmpty()) {
                        deliveryCategorizationResultsQueue.add(String.format("%s  -->  %s", topic, message));
                    }
                }
            }
        }

        private String safeTakeFromQueue() {
            try {
                return deliveryAsrResultsQueue.take();
            } catch (InterruptedException e) {
                System.out.println(e.getMessage());
            }
            return null;
        }
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) throws Exception {
        super.channelUnregistered(ctx);
        categorizationWorker.interrupt();
    }
}
