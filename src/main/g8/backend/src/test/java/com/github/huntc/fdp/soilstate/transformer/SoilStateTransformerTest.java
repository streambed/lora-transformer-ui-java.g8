package com.github.huntc.fdp.soilstate.transformer;

import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import akka.util.ByteString;
import com.github.huntc.fdp.soilstate.SoilStateReading;
import com.github.huntc.streambed.durablequeue.DurableQueue;
import com.github.huntc.streambed.identity.Principal;
import com.github.huntc.streambed.testkit.durablequeue.InMemoryQueue\$;
import io.opentracing.noop.NoopTracer;
import io.opentracing.noop.NoopTracerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.Option;
import scala.Tuple2;
import scala.concurrent.duration.FiniteDuration;
import scala.util.Either;
import scala.util.Right;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static com.github.huntc.streambed.HexString.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SoilStateTransformerTest {

    private static ActorSystem system;
    private static Materializer mat;

    @Before
    public void setUp() {
        system = ActorSystem.create();
        mat = ActorMaterializer.create(system);
    }

    @After
    public void tearDown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void transform() throws InterruptedException, ExecutionException, TimeoutException {
        DurableQueue durableQueue = InMemoryQueue\$.MODULE\$.queue(mat, system);
        String encryptionKey = "2B7E151628AED2A6ABF7158809CF4F3C";
        Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret = secret ->
                CompletableFuture.completedFuture(Right.apply(
                        new Principal.SecretRetrieved(new Principal.AuthorizedSecret(encryptionKey,
                                new FiniteDuration(10, TimeUnit.SECONDS)))));

        NoopTracer tracer = NoopTracerFactory.create();

        // Kick off the transformer
        SoilStateTransformer
                .source(durableQueue, getSecret, tracer, mat)
                .runWith(Sink.ignore(), mat);

        /*
         * Enqueue a LoRaWAN payload as a Network Server would. Uses the packet encoder utility to obtain
         * these values i.e.:
         *
         * docker run --rm farmco/lora-packet-encoder:0.9.0 \
         *   2B7E151628AED2A6ABF7158809CF4F3C \
         *   49be7df1 \
         *   2b11ff0d
         *
         * The first param is the AppSKey as hex, the second is the DevAddr as hex and the third
         * is the soilstate payload as hex.
         */
        int nwkAddr = hexToInt("01be7df1");
        byte[] payload = hexToBytes("40f17dbe49000200017e84fa392b11ff0d");

        Source
                .single(
                        new DurableQueue.CommandRequest<>(
                                new DurableQueue.Send(
                                        nwkAddr,
                                        ByteString.fromArray(payload),
                                        SoilStateTransformer.DATA_UP_MAC_PAYLOAD_TOPIC,
                                        DurableQueue.EmptyHeaders()),
                                Option.empty()))
                .via(durableQueue.flow())
                .runWith(Sink.head(), mat);

        Tuple2<SoilStateReading, Long> result = durableQueue
                .source(SoilStateReading.DATA_UP_JSON_TOPIC)
                .via(SoilStateReading.tailer(getSecret, mat.executionContext()))
                .runWith(Sink.head(), mat)
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS);

        SoilStateReading reading = result._1();
        assertTrue(reading.getTime().isBefore(Instant.now()));
        assertEquals(nwkAddr, reading.getNwkAddr());
        assertEquals(BigDecimal.valueOf(200, 1), reading.getTemperature());
        assertEquals(BigDecimal.valueOf(161, 1), reading.getMoisturePercentage());
    }
}