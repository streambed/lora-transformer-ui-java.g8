package com.github.huntc.fdp.soilstate;

// #domain

import akka.NotUsed;
import akka.stream.ActorAttributes;
import akka.stream.Supervision;
import akka.stream.javadsl.Flow;
import akka.util.ByteString;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.huntc.streambed.durablequeue.DurableQueue;
import com.github.huntc.streambed.identity.Principal;
import com.github.huntc.streambed.identity.streams.Streams;
import scala.Option;
import scala.Tuple2;
import scala.compat.java8.FutureConverters;
import scala.concurrent.ExecutionContext;
import scala.util.Either;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Captures a temperature/moisture reading of soil.
 */
public final class SoilStateReading {
    // #constants
    /**
     * The topic used to send encrypted domain object data to and consume it from
     */
    public static final String DATA_UP_JSON_TOPIC = "soilstate-data-up-json";

    /**
     * The path of the secret to be used for encrypting and decrypting the domain object
     */
    public static final String KEY_PATH = "secrets.soilstate.key";
    // #constants

    // #json
    public static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    private final Instant time;

    private final int nwkAddr;

    private final BigDecimal temperature;

    private final BigDecimal moisturePercentage;

    @JsonCreator
    public SoilStateReading(@JsonProperty(value = "time", required = true) Instant time,
                            @JsonProperty(value = "nwkAddr", required = true) int nwkAddr,
                            @JsonProperty(value = "temperature", required = true) BigDecimal temperature,
                            @JsonProperty(value = "moisturePercentage", required = true) BigDecimal moisturePercentage) {
        this.time = time;
        this.nwkAddr = nwkAddr;
        this.temperature = temperature;
        this.moisturePercentage = moisturePercentage;
    }
    // #json

    // #fromBytes

    /**
     * Construct from raw bytes
     */
    public SoilStateReading(Instant time, int nwkAddr, byte[] payload) {
        this.time = time;
        this.nwkAddr = nwkAddr;
        if (payload.length >= 4) {
            ByteBuffer iter = ByteBuffer.wrap(payload);
            BigDecimal TEMP_OFFSET = BigDecimal.valueOf(40, 0);
            BigDecimal t = BigDecimal.valueOf(iter.getShort() & 0xFFFF, 1).subtract(TEMP_OFFSET);
            BigDecimal m = BigDecimal.valueOf(iter.getShort() & 0xFFFF, 1);
            this.temperature = t;
            this.moisturePercentage = m;
        } else {
            this.temperature = BigDecimal.ZERO;
            this.moisturePercentage = BigDecimal.ZERO;
        }
    }
    // #fromBytes

    public Instant getTime() {
        return time;
    }

    public int getNwkAddr() {
        return nwkAddr;
    }

    public BigDecimal getTemperature() {
        return temperature;
    }

    public BigDecimal getMoisturePercentage() {
        return moisturePercentage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SoilStateReading that = (SoilStateReading) o;
        return nwkAddr == that.nwkAddr &&
                Objects.equals(time, that.time) &&
                Objects.equals(temperature, that.temperature) &&
                Objects.equals(moisturePercentage, that.moisturePercentage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, nwkAddr, temperature, moisturePercentage);
    }

    @Override
    public String toString() {
        return "SoilStateReading{" +
                "time=" + time +
                ", nwkAddr=" + nwkAddr +
                ", temperature=" + temperature +
                ", moisturePercentage=" + moisturePercentage +
                '}';
    }

    /**
     * A convenience function for encoding an reading, encrypting it and then
     * publishing it to a queue.
     */
    public static Flow<SoilStateReading, DurableQueue.CommandRequest<Object>, NotUsed>
    appender(
            Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret,
            ExecutionContext ec
    ) {
        return Flow.<SoilStateReading>create()
                .map(e -> new Tuple2<>(e.getNwkAddr(), ByteString.fromString(mapper.writeValueAsString(e))))
                .map(e -> {
                    int nwkAddr = e._1();
                    ByteString bytes = e._2();
                    return new Tuple2<>(new Tuple2<>(FutureConverters.toScala(getSecret.apply(KEY_PATH)), bytes), nwkAddr);
                })
                .via(Streams.encrypter(ec))
                .map(e -> {
                    ByteString bytes = e._1();
                    int nwkAddr = e._2();
                    return new DurableQueue.CommandRequest<>(
                            new DurableQueue.Send(
                                    nwkAddr,
                                    bytes,
                                    SoilStateReading.DATA_UP_JSON_TOPIC,
                                    DurableQueue.EmptyHeaders()),
                            Option.empty());
                });
    }

    /**
     * Conveniently tail, decrypt and decode readings. Yields the reading and its offset.
     */
    public static Flow<DurableQueue.Received, Tuple2<SoilStateReading, Long>, NotUsed>
    tailer(Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret,
           ExecutionContext ec) {
        return Flow.<DurableQueue.Received>create()
                .map(e -> {
                    ByteString encryptedData = e.data();
                    long o = e.offset();
                    return new Tuple2<>(new Tuple2<>(FutureConverters.toScala(getSecret.apply(KEY_PATH)), encryptedData), o);
                })
                .via(Streams.decrypter(ec))
                .map(e -> {
                    ByteString data = e._1();
                    long o = e._2();
                    return new Tuple2<>(mapper.readValue(data.toArray(), SoilStateReading.class), o);
                })
                .withAttributes(ActorAttributes.withSupervisionStrategy(Supervision.getResumingDecider()));
    }

}
// #domain
