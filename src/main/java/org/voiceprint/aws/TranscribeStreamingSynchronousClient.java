package org.voiceprint.aws;


import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponseHandler;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;

import java.io.InputStream;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TranscribeStreamingSynchronousClient {

    public static final int MAX_TIMEOUT_MS = 15 * 60 * 1000; //15 minutes

    private final TranscribeStreamingAsyncClient asyncClient;
    private final Deque<String> results4delivery;

    public TranscribeStreamingSynchronousClient(TranscribeStreamingAsyncClient asyncClient,
                                                Deque<String> results4delivery) {
        this.asyncClient = asyncClient;
        this.results4delivery = results4delivery;
    }

    public void transcribe(InputStream audio) {
        try {
            StartStreamTranscriptionRequest request = StartStreamTranscriptionRequest.builder()
                    .languageCode(LanguageCode.EN_US.toString())
                    .mediaEncoding(MediaEncoding.PCM)
                    .mediaSampleRateHertz(16000)
                    .build();

            AudioStreamPublisher audioStream = new AudioStreamPublisher(audio);

            StartStreamTranscriptionResponseHandler responseHandler = getResponseHandler();
            CompletableFuture<Void> resultFuture = asyncClient.startStreamTranscription(request, audioStream, responseHandler);

            // blocks until all stream will be recognized
            resultFuture.get();
        }  catch (Exception e) {
            System.out.println("Stream not closed within timeout window of " + MAX_TIMEOUT_MS + " ms");
            throw new RuntimeException(e);
        }
    }

    /**
     * Get a response handler that aggregates the transcripts as they arrive
     * @return Response handler used to handle events from AWS Transcribe service.
     */
    private StartStreamTranscriptionResponseHandler getResponseHandler() {
        return StartStreamTranscriptionResponseHandler.builder()
                .subscriber(event -> {
                    List<Result> results = ((TranscriptEvent) event).transcript().results();
                    if(results.size()>0) {
                        Result firstResult = results.get(0);
                        if (firstResult.alternatives().size() > 0 &&
                                !firstResult.alternatives().get(0).transcript().isEmpty()) {
                            String transcript = firstResult.alternatives().get(0).transcript();
                            if(!transcript.isEmpty() && !firstResult.isPartial()) {
                                results4delivery.add(transcript);
                            }
                        }

                    }
                }).build();
    }

}
