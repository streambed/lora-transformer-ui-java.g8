package $organization;format="package"$.$deviceType;format="camel"$.transformer;

// #transformer

import akka.NotUsed;
import akka.japi.JavaPartialFunction;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Source;
import akka.util.ByteString;
import $organization;format="package"$.$deviceType;format="camel"$.$deviceType;format="Camel"$Reading;
import com.cisco.streambed.UuidOps;
import com.cisco.streambed.durablequeue.DurableQueue;
import com.cisco.streambed.durablequeue.opentracing.Headers;
import com.cisco.streambed.identity.Principal;
import com.cisco.streambed.lora.streams.Streams;
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
 * to their $deviceType$ domain object expressed in json and then
 * re-published. The packets received on this topic have already
 * been verified by an NS and so do not require MIC or counter
 * verification. Any MacPayload data that is not ConfirmedDataUp
 * or UnconfirmedDataUp can also be safely ignored as it should
 * not be received here.
 */
public final class $deviceType;format="Camel"$Transformer {
    /**
     * The durable queue topic where LoRaWAN packets are consumed from
     */
    static final String DATA_UP_MAC_PAYLOAD_TOPIC = "$deviceType;format="norm"$-data-up-mac-payload";

    /**
     * Provides a source to perform the transformation.
     */
    public static Source<Span, NotUsed>
    source(DurableQueue durableQueue,
           Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret,
           Tracer tracer,
           Materializer mat) {

        Flow<DurableQueue.Received, Span, NotUsed> transform = Flow.<DurableQueue.Received>create()
                .named("$deviceType;format="norm"$-transformer")
                .log("$deviceType;format="norm"$-transformer")
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
                .map(e -> {
                    ByteString received = e._1();
                    SpanContext spanContext = e._2();
                    try (Scope scope = tracer
                            .buildSpan("$deviceType;format="norm"$-transformation")
                            .addReference(References.FOLLOWS_FROM, spanContext)
                            .startActive(false)) {
                        Span span = scope.span();
                        return new Tuple2<>(received, span);
                    }
                })
                .via(Streams.dataUpDecoder(getSecret, mat.executionContext()))
                .map(e -> {
                    int nwkAddr = e._1()._1();
                    ByteString payload = e._1()._2();
                    Span span = e._2();
                    return new Tuple2<>(
                            new $deviceType;format="Camel"$Reading(
                                    Instant.now(),
                                    nwkAddr,
                                    payload.toArray()), span);
                })
                .map(e -> {
                    $deviceType;format="Camel"$Reading reading = e._1();
                    Span span = e._2();
                    return new Tuple2<>(
                            new Tuple2<>(
                                    reading.getNwkAddr(),
                                    $deviceType;format="Camel"$Reading.mapper.writeValueAsString(reading)),
                            span);
                })
                .map(e -> {
                    int nwkAddr = e._1()._1();
                    String decryptedData = e._1()._2();
                    Span span = e._2();
                    return new Tuple2<>(
                            new Tuple2<>(
                                    FutureConverters.toScala(getSecret.apply($deviceType;format="Camel"$Reading.KEY_PATH)),
                                    ByteString.fromString(decryptedData)),
                            new Tuple2<>(nwkAddr, span));
                })
                .via(com.cisco.streambed.identity.streams.Streams.encrypter(mat.executionContext()))
                .map(e -> {
                    ByteString encryptedData = e._1();
                    long key = e._2()._1();
                    Span span = e._2()._2();
                    return new DurableQueue.CommandRequest<>(
                            new DurableQueue.Send(
                                    key,
                                    encryptedData,
                                    $deviceType;format="Camel"$Reading.DATA_UP_JSON_TOPIC,
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
                .wireTap(span -> tracer.scopeManager().activate(span, true).close());

        return durableQueue
                .resumableSource(
                        // #c
                        DATA_UP_MAC_PAYLOAD_TOPIC,
                        UuidOps.v5($deviceType;format="Camel"$Transformer.class),
                        transform.asScala()
                ).asJava();
    }
}
