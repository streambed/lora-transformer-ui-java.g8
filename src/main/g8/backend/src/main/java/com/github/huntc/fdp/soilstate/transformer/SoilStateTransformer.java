package com.github.huntc.fdp.soilstate.transformer;

// #transformer

import akka.NotUsed;
import akka.japi.JavaPartialFunction;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import com.github.huntc.fdp.soilstate.SoilStateReading;
import com.github.huntc.lora.streams.Streams;
import com.github.huntc.streambed.UuidOps;
import com.github.huntc.streambed.durablequeue.DurableQueue;
import com.github.huntc.streambed.durablequeue.opentracing.Headers;
import com.github.huntc.streambed.identity.Principal;
import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import scala.Tuple2;
import scala.collection.Seq;
import scala.compat.java8.FutureConverters;
import scala.compat.java8.OptionConverters;
import scala.util.Either;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Run the transformation process to convert from LoRaWAN packets
 * to their soilstate domain object expressed in json and then
 * re-published. The packets received on this topic have already
 * been verified by an NS and so do not require MIC or counter
 * verification. Any MacPayload data that is not ConfirmedDataUp
 * or UnconfirmedDataUp can also be safely ignored as it should
 * not be received here.
 */
public final class SoilStateTransformer {
    // #a
    /**
     * The durable queue topic where LoRaWAN packets are consumed from
     */
    static final String DATA_UP_MAC_PAYLOAD_TOPIC = "soilstate-data-up-mac-payload";
    // #a

    // #b
    /**
     * Provides a source to perform the transformation.
     */
    public static Source<Span, NotUsed>
    source(DurableQueue durableQueue,
           Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret,
           Tracer tracer,
           Materializer mat) {
        // #b

        // #d
        Flow<DurableQueue.Received, Span, NotUsed> transform = Flow.<DurableQueue.Received>create()
                .named("soilstate")
                .log("soilstate")
                .collectType(DurableQueue.Received.class)
                .map(e -> {
                    ByteString data = e.data();
                    Seq<Tuple2<String, ByteString>> headers = e.headers();
                    return new Tuple2<>(data, headers);
                })
                .map(e -> {
                    ByteString data = e._1();
                    Seq<Tuple2<String, ByteString>> headers = e._2();
                    return new Tuple2<>(data, Headers.spanContext(headers, tracer));
                })
                // #d
                // #e
                .map(e -> {
                    ByteString received = e._1();
                    SpanContext spanContext = e._2();
                    try (Scope scope = tracer
                            .buildSpan("soilstate-transformation")
                            .addReference(References.FOLLOWS_FROM, spanContext)
                            .startActive(false)) {
                        Span span = scope.span();
                        return new Tuple2<>(received, span);
                    }
                })
                // #e
                // #f
                .via(Streams.dataUpDecoder(getSecret, mat.executionContext()))
                // #f
                // #g
                .map(e -> {
                    int nwkAddr = e._1()._1();
                    ByteString payload = e._1()._2();
                    Span span = e._2();
                    return new Tuple2<>(
                            new SoilStateReading(
                                    Instant.now(),
                                    nwkAddr,
                                    payload.toArray()), span);
                })
                // #g
                // #h
                .map(e -> {
                    SoilStateReading reading = e._1();
                    Span span = e._2();
                    return new Tuple2<>(
                            new Tuple2<>(
                                    reading.getNwkAddr(),
                                    SoilStateReading.mapper.writeValueAsString(reading)),
                            span);
                })
                // #h
                // #i
                .map(e -> {
                    int nwkAddr = e._1()._1();
                    String decryptedData = e._1()._2();
                    Span span = e._2();
                    return new Tuple2<>(
                            new Tuple2<>(
                                    FutureConverters.toScala(getSecret.apply(SoilStateReading.KEY_PATH)),
                                    ByteString.fromString(decryptedData)),
                            new Tuple2<>(nwkAddr, span));
                })
                .via(com.github.huntc.streambed.identity.streams.Streams.encrypter(mat.executionContext()))
                // #i
                // #j
                .map(e -> {
                    ByteString encryptedData = e._1();
                    long key = e._2()._1();
                    Span span = e._2()._2();
                    return new DurableQueue.CommandRequest<>(
                            new DurableQueue.Send(
                                    key,
                                    encryptedData,
                                    SoilStateReading.DATA_UP_JSON_TOPIC,
                                    Headers.headers(span.context(), tracer)),
                            OptionConverters.toScala(Optional.of(span)));

                })
                .via(durableQueue.flow())
                .collect(new JavaPartialFunction<DurableQueue.CommandReply<Span>, Span>() {
                    @Override
                    public Span apply(DurableQueue.CommandReply<Span> x, boolean isCheck) {
                        if (x.event() instanceof DurableQueue.SendAck\$)
                            return x.carry().get();
                        else
                            throw noMatch();
                    }
                })
                // #j
                // #k
                .wireTap(span -> tracer.scopeManager().activate(span, true).close());
                // #k

        // #c
        return durableQueue
                .resumableSource(
                        // #c
                        DATA_UP_MAC_PAYLOAD_TOPIC,
                        UuidOps.v5(SoilStateTransformer.class),
                        transform.asScala()
                ).asJava();
        // #c
    }
}
// #transformer
