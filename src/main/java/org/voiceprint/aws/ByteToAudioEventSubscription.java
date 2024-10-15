package org.voiceprint.aws;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.transcribestreaming.model.AudioEvent;
import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class ByteToAudioEventSubscription implements Subscription {

    private static final int CHUNK_SIZE_IN_BYTES = 1024 * 4;
    private ExecutorService executor = Executors.newFixedThreadPool(1);
    private AtomicLong demand = new AtomicLong(0);

    private final Subscriber<? super AudioStream> subscriber;
    private final InputStream inputStream;

    public ByteToAudioEventSubscription(Subscriber<? super AudioStream> s, InputStream inputStream) {
        this.subscriber = s;
        this.inputStream = inputStream;
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            subscriber.onError(new IllegalArgumentException("Demand must be positive"));
        }

        demand.getAndAdd(n);
        //We need to invoke this in a separate thread because the call to subscriber.onNext(...) is recursive
        executor.submit(() -> {
            try {
                do {
                    ByteBuffer audioBuffer = getNextEvent();
                    if (audioBuffer.remaining() > 0) {
                        AudioEvent audioEvent = audioEventFromBuffer(audioBuffer);
                        subscriber.onNext(audioEvent);
                    } else {
                        subscriber.onComplete();
                        break;
                    }
                } while (demand.decrementAndGet() > 0);
            } catch (Exception e) {
                subscriber.onError(e);
            }
        });
    }

    @Override
    public void cancel() {
        executor.shutdown();
    }


    private ByteBuffer getNextEvent() {
        ByteBuffer audioBuffer;
        byte[] audioBytes = new byte[CHUNK_SIZE_IN_BYTES];

        try {
            int len = inputStream.read(audioBytes);

            if (len <= 0) {
                audioBuffer = ByteBuffer.allocate(0);
            } else {
                audioBuffer = ByteBuffer.wrap(audioBytes, 0, len);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return audioBuffer;
    }

    public static byte[][] splitArray(byte[] originalArray, int fixedSize) {
        byte[] firstArray = new byte[fixedSize];
        byte[] secondArray = new byte[originalArray.length - fixedSize];

        // Copy elements from the original array to the first array
        for (int i = 0; i < fixedSize; i++) {
            firstArray[i] = originalArray[i];
        }

        // Copy the remaining elements to the second array
        int secondArrayIndex = 0;
        for (int i = fixedSize; i < originalArray.length; i++) {
            secondArray[secondArrayIndex++] = originalArray[i];
        }

        return new byte[][]{firstArray, secondArray};
    }

    private AudioEvent audioEventFromBuffer(ByteBuffer bb) {
        return AudioEvent.builder()
                .audioChunk(SdkBytes.fromByteBuffer(bb))
                .build();
    }
}
