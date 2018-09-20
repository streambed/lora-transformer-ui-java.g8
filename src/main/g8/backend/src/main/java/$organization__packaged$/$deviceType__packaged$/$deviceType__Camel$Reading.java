package $organization;format="package"$.$deviceType;format="camel"$;

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
import com.cisco.streambed.durablequeue.DurableQueue;
import com.cisco.streambed.identity.Principal;
import com.cisco.streambed.identity.streams.Streams;
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
 * Captures a sensor observation.
 */
public final class $deviceType;format="Camel"$Reading {
    /**
     * The topic used to send encrypted domain object data to and consume it from
     */
    public static final String DATA_UP_JSON_TOPIC = "$deviceType;format="norm"$-data-up-json";

    /**
     * The path of the secret to be used for encrypting and decrypting the domain object
     */
    public static final String KEY_PATH = "secrets.$deviceType;format="norm"$.key";

    public static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    // FIXME: These fields and its constructor should be as per your sensor

    private final Instant time;

    private final int nwkAddr;

    private final BigDecimal temperature;

    private final BigDecimal moisturePercentage;

    @JsonCreator
    public $deviceType;format="Camel"$Reading(@JsonProperty(value = "time", required = true) Instant time,
                            @JsonProperty(value = "nwkAddr", required = true) int nwkAddr,
                            @JsonProperty(value = "temperature", required = true) BigDecimal temperature,
                            @JsonProperty(value = "moisturePercentage", required = true) BigDecimal moisturePercentage) {
        this.time = time;
        this.nwkAddr = nwkAddr;
        this.temperature = temperature;
        this.moisturePercentage = moisturePercentage;
    }

    /**
     * Construct from a decrypted LoRaWAN ConfirmedDataUp/UnconfirmedDataUp FRMPayload
     *
     * FIXME: The following example assumes a soil moisture/temperature sensors with 4 bytes.
     * The first 2 bytes are the temperature in Celsuis and 400 added to it (for fun).
     * The second 2 bytes represent the moisture as a percentage.
     *
     * You should change this function to decode your LoRaWAN payload from its byte
     * representation.
     */
    public $deviceType;format="Camel"$Reading(Instant time, int nwkAddr, byte[] payload) {
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
        $deviceType;format="Camel"$Reading that = ($deviceType;format="Camel"$Reading) o;
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
        return "$deviceType;format="Camel"$Reading{" +
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
    public static Flow<$deviceType;format="Camel"$Reading, DurableQueue.CommandRequest<Object>, NotUsed>
    appender(
            Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret,
            ExecutionContext ec
    ) {
        return Flow.<$deviceType;format="Camel"$Reading>create()
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
                                    $deviceType;format="Camel"$Reading.DATA_UP_JSON_TOPIC,
                                    DurableQueue.EmptyHeaders()),
                            Option.empty());
                });
    }

    /**
     * Conveniently tail, decrypt and decode readings. Yields the reading and its offset.
     */
    public static Flow<DurableQueue.Received, Tuple2<$deviceType;format="Camel"$Reading, Long>, NotUsed>
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
                    return new Tuple2<>(mapper.readValue(data.toArray(), $deviceType;format="Camel"$Reading.class), o);
                })
                .withAttributes(ActorAttributes.withSupervisionStrategy(Supervision.getResumingDecider()));
    }

}
// #domain
