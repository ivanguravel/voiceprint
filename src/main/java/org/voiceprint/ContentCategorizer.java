package org.voiceprint;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.ahocorasick.trie.PayloadTrie;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ContentCategorizer extends ChannelInboundHandlerAdapter {

    private final Map<String, PayloadTrie<String>> categories = new ConcurrentHashMap<>();
    Deque<String> input = new ConcurrentLinkedDeque<>();
    Deque<String> output = new ConcurrentLinkedDeque<>();

    public ContentCategorizer() {
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

        new Thread(new ContentFinder()).start();
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        input.add(msg.toString());
        while (!output.isEmpty()) {
            ctx.fireChannelRead(output.poll());
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


    class ContentFinder implements Runnable {
        @Override
        public void run() {
            while (true) {
                while (!input.isEmpty()) {
                    String message = input.poll();
                    Set<String> topics = categories.keySet();
                    for (String topic : topics) {
                        PayloadTrie<String> trie = categories.get(topic);
                        if (!trie.parseText(message).isEmpty()) {
                            output.add(String.format("%s  -->  %s", topic, message));
                        }
                    }
                }
            }
        }
    }
}
