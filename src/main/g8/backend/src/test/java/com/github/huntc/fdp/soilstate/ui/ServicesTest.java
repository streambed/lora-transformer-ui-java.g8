package com.github.huntc.fdp.soilstate.ui;

import akka.Done;
import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.japi.pf.PFBuilder;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import akka.testkit.javadsl.TestKit;
import com.github.huntc.fdp.soilstate.SoilStateReading;
import com.github.huntc.lora.controlplane.EndDeviceEvents;
import com.github.huntc.streambed.durablequeue.DurableQueue;
import com.github.huntc.streambed.testkit.durablequeue.InMemoryQueue\$;
import com.github.huntc.streambed.identity.Principal;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.function.Function;

import scala.Option;
import scala.PartialFunction;
import scala.Tuple2;
import scala.concurrent.duration.FiniteDuration;
import scala.math.BigDecimal;
import scala.util.Either;
import scala.util.Right;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.*;

public class ServicesTest {

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
    public void endDeviceEvents() throws InterruptedException, ExecutionException, TimeoutException {
        DurableQueue durableQueue = InMemoryQueue\$.MODULE\$.queue(mat, system);
        Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret = secret ->
                CompletableFuture.completedFuture(Right.apply(
                        new Principal.SecretRetrieved(new Principal.AuthorizedSecret("2B7E151628AED2A6ABF7158809CF4F3C",
                                new FiniteDuration(10, TimeUnit.SECONDS)))));

        CompletionStage<Done> done =
                Source
                        .<EndDeviceEvents.Event>from(
                                Arrays.asList(
                                        new EndDeviceEvents.TopicUpdated(1, SoilStateReading.DATA_UP_JSON_TOPIC),
                                        new EndDeviceEvents.NwkAddrRemoved(1),
                                        new EndDeviceEvents.PositionUpdated(1, Instant.now(), new EndDeviceEvents.LatLng(BigDecimal.valueOf(0), BigDecimal.valueOf(0), Option.empty()))
                                ))
                        .via(EndDeviceEvents.appender(getSecret, mat.executionContext()))
                        .via(durableQueue.flow())
                        .runWith(Sink.ignore(), mat);

        done.toCompletableFuture().get(3, TimeUnit.SECONDS);

        PartialFunction<Throwable, Source<Tuple2<EndDeviceEvents.Event, Long>, NotUsed>> completer = new PFBuilder<Throwable, Source<Tuple2<EndDeviceEvents.Event, Long>, NotUsed>>()
                .match(TimeoutException.class, ex -> Source.empty()).build();

        List<Tuple2<EndDeviceEvents.Event, Long>> events =
                EndDeviceService.events(durableQueue, 2, getSecret, mat)
                        .idleTimeout(Duration.of(1, ChronoUnit.SECONDS)).recoverWithRetries(1, completer)
                        .runWith(Sink.seq(), mat).toCompletableFuture().get(3, TimeUnit.SECONDS);

        assertEquals(1, events.size());
    }

    @Test
    public void soilstateEvents() throws InterruptedException, ExecutionException, TimeoutException {
        DurableQueue durableQueue = InMemoryQueue\$.MODULE\$.queue(mat, system);
        Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret = secret ->
                CompletableFuture.completedFuture(Right.apply(
                        new Principal.SecretRetrieved(new Principal.AuthorizedSecret("2B7E151628AED2A6ABF7158809CF4F3C",
                                new FiniteDuration(10, TimeUnit.SECONDS)))));

        SoilStateReading reading = new SoilStateReading(Instant.EPOCH, 1, java.math.BigDecimal.valueOf(20), java.math.BigDecimal.valueOf(122, 1));

        CompletionStage<Done> done =
                Source.single(reading)
                        .via(SoilStateReading.appender(getSecret, mat.executionContext()))
                        .flatMapConcat(e -> Source.repeat(e).take(100))
                        .via(durableQueue.flow())
                        .runWith(Sink.ignore(), mat);

        done.toCompletableFuture().get(3, TimeUnit.SECONDS);

        PartialFunction<Throwable, Source<Tuple2<SoilStateReading, Long>, NotUsed>> completer = new PFBuilder<Throwable, Source<Tuple2<SoilStateReading, Long>, NotUsed>>()
                .match(TimeoutException.class, ex -> Source.empty()).build();

        List<Tuple2<SoilStateReading, Long>> events =
                SoilstateService.events(durableQueue, 1, getSecret, mat)
                        .idleTimeout(Duration.of(1, ChronoUnit.SECONDS)).recoverWithRetries(1, completer)
                        .runWith(Sink.seq(), mat).toCompletableFuture().get(3, TimeUnit.SECONDS);

        assertEquals(1, events.size());
    }}
