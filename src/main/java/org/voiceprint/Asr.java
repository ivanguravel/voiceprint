package org.voiceprint;

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.io.IOException;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;

class Asr extends ChannelInboundHandlerAdapter {

    private final RecognitionConfig recognitionConfig;
    private final GoogleCredentials credentials;
    private final Deque<String> results4delivery;
    private final Deque<byte[]> voice4recognition;

    private SpeechClient client;
    private ClientStream<StreamingRecognizeRequest> streamingClient;
    private ResponseObserver<StreamingRecognizeResponse> responseObserver;

    public Asr(RecognitionConfig recognitionConfig, GoogleCredentials credentials) {
        this.recognitionConfig = recognitionConfig;
        this.credentials = credentials;
        this.results4delivery = new ConcurrentLinkedDeque<>();
        this.voice4recognition = new ConcurrentLinkedDeque<>();

        // TODO: as per Google documentation we should prepare restart of the client
        // https://cloud.google.com/speech-to-text/quotas#:~:text=Content%20to%20Speech%2Dto%2DText,the%20API%20using%20local%20files
        initializeSpeechClient();
        this.responseObserver = createAsrResponse();
        initializeStreamingClient();


        new Thread(new GoogleAsrDeliveryWorker()).start();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf buffer = (ByteBuf) msg;
        try {
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            voice4recognition.add(bytes);
            while (!results4delivery.isEmpty()) {
                String words = results4delivery.poll();
                ctx.fireChannelRead(words);
            }
        } finally {
            buffer.release();
        }
    }

    class GoogleAsrDeliveryWorker implements Runnable {

        @Override
        public void run() {
            while (true) {
                if (!voice4recognition.isEmpty()) {
                    byte[] data = voice4recognition.poll();
                    StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
                            .setAudioContent(ByteString.copyFrom(data))
                            .build();
                    streamingClient.send(request);
                }
            }
        }
    }


    private ResponseObserver<StreamingRecognizeResponse> createAsrResponse() {
        return new ResponseObserver<>() {

            public void onStart(StreamController controller) {
            }

            public void onResponse(StreamingRecognizeResponse response) {
                List<StreamingRecognitionResult> resultsFromAsr = response.getResultsList();
                if (Objects.nonNull(resultsFromAsr)) {
                    for (StreamingRecognitionResult recognitionResult : resultsFromAsr) {
                        if (recognitionResult.getIsFinal()) {
                            results4delivery.add(recognitionResult.getAlternatives(0).getTranscript());
                        }
                    }
                }
            }

            public void onComplete() {
                System.err.println("Asr work has been completed");
            }

            public void onError(Throwable t) {
                throw new RuntimeException(t);
            }
        };
    }

    private void initializeSpeechClient() {
        try {
            this.client = SpeechClient
                    .create(SpeechSettings
                    .newBuilder()
                    .setCredentialsProvider(() -> this.credentials)
                    .build());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeStreamingClient() {
        streamingClient = client.streamingRecognizeCallable().splitCall(responseObserver);

        StreamingRecognitionConfig streamingRecognitionConfig = StreamingRecognitionConfig
                .newBuilder()
                .setConfig(recognitionConfig)
                .build();

        StreamingRecognizeRequest request = StreamingRecognizeRequest.newBuilder()
                .setStreamingConfig(streamingRecognitionConfig)
                .build();

        streamingClient.send(request);
    }
}
