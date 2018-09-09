package $organization;format="package"$.$deviceType;format="camel"$.ui;

import akka.NotUsed;
import akka.stream.Materializer;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.github.huntc.lora.controlplane.EndDeviceEvents;
import com.github.huntc.streambed.durablequeue.DurableQueue;
import com.github.huntc.streambed.identity.Principal;
import scala.Option;
import scala.Tuple2;
import scala.compat.java8.OptionConverters;
import scala.util.Either;

import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Manages services in relation to end devices.
 */
class EndDeviceService {
    /**
     * Subscribe to the end device events queue returning a source of events and their offsets in the queue.
     * We always start at the tail of a queue, relying on external processes to cleanup the tail and therefore,
     * the length of the queue, sane.
     *
     * To keep the UI sane, we only emit the latest end device events that the UI will be interested in i.e.
     * position and removal. We then continue to emit events that we are able to decrypt, which may, or may
     * not be related to the $deviceType$ topic. For simplicity sake, while we could continue to event source those,
     * we let the UI do that as it must anyhow.
     */
    static Source<Tuple2<EndDeviceEvents.Event, Long>, NotUsed> events(DurableQueue durableQueue,
                                                                       int maxInitialSensors,
                                                                       Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret,
                                                                       Materializer mat) {
        return Source.fromSourceCompletionStage(
                durableQueue.source(EndDeviceEvents.EventTopic(), Option.empty(), true).<DurableQueue.Received, NotUsed>asJava()
                        .via(EndDeviceEvents.tailer(getSecret, mat.executionContext()))
                        .groupBy(maxInitialSensors, e -> e._1.nwkAddr())
                        .reduce((e0, e1) -> (isUiEvent((e1))? e1 : e0))
                        .mergeSubstreams()
                        .runWith(Sink.seq(), mat)
                        .thenApply(lastEvents -> {
                            OptionalLong lastOffset = (lastEvents.isEmpty() ? OptionalLong.empty() : OptionalLong.of(lastEvents.get(lastEvents.size() - 1)._2));
                            return Source
                                    .from(lastEvents)
                                    .concat(
                                            durableQueue
                                                    .source(EndDeviceEvents.EventTopic(), OptionConverters.toScala(lastOffset)).<DurableQueue.Received, NotUsed>asJava()
                                                    .dropWhile(r -> lastOffset.isPresent() && lastOffset.getAsLong() == r.offset()) // offsets are inclusive, so skip one if we have one
                                                    .via(EndDeviceEvents.tailer(getSecret, mat.executionContext())))
                                    .filter(EndDeviceService::isUiEvent);
                        })
        ).mapMaterializedValue(me -> NotUsed.notUsed());
    }

    private static boolean isUiEvent(Tuple2<EndDeviceEvents.Event, Long> e) {
        return (e._1 instanceof EndDeviceEvents.PositionUpdated || e._1 instanceof EndDeviceEvents.NwkAddrRemoved);
    }
}
