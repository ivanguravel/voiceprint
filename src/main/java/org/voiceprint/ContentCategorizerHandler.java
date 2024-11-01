package org.voiceprint;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.ahocorasick.trie.PayloadTrie;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ContentCategorizerHandler extends ChannelInboundHandlerAdapter {

    private final Map<String, PayloadTrie<String>> categories = new ConcurrentHashMap<>();
    Deque<String> deliveryAsrResultsQueue = new ConcurrentLinkedDeque<>();
    Deque<String> deliveryCategorizationResultsQueue = new ConcurrentLinkedDeque<>();

    public ContentCategorizerHandler() {
        categories.put("Amazon", buildTrieFromWords(Arrays.asList(
                "Amazon",
                "AWS",
                "Lambda",
                "ec2",
                "ECS",
                "elastic container registry",
                "elastic load balancing"
        )));
        categories.put("General Tech Things", buildTrieFromWords(Arrays.asList(
                "Docker",
                "WordPress",
                "Platform"
        )));

        new Thread(new ContentCategorizationWorker()).start();
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        deliveryAsrResultsQueue.add(msg.toString());
        while (!deliveryCategorizationResultsQueue.isEmpty()) {
            ctx.fireChannelRead(deliveryCategorizationResultsQueue.poll());
        }
    }


    private static PayloadTrie<String> buildTrieFromWords(List<String> words) {
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
            while (true) {
                while (!deliveryAsrResultsQueue.isEmpty()) {
                    String message = deliveryAsrResultsQueue.poll();
                    Set<String> topics = categories.keySet();
                    for (String topic : topics) {
                        PayloadTrie<String> trie = categories.get(topic);
                        if (!trie.parseText(message).isEmpty()) {
                            deliveryCategorizationResultsQueue.add(String.format("%s  -->  %s", topic, message));
                        }
                    }
                }
            }
        }
    }
}
