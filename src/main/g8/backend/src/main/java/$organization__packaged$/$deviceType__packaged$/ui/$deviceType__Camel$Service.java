package $organization;format="package"$.$deviceType;format="camel"$.ui;

import akka.NotUsed;
import akka.stream.ActorAttributes;
import akka.stream.Materializer;
import akka.stream.Supervision;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import $organization;format="package"$.$deviceType;format="camel"$.$deviceType;format="Camel"$Reading;
import com.cisco.streambed.durablequeue.DurableQueue;
import com.cisco.streambed.identity.Principal;
import scala.compat.java8.OptionConverters;
import scala.Option;
import scala.Tuple2;
import scala.util.Either;

import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Manages services in relation to $deviceType$.
 */
class $deviceType;format="Camel"$Service {
    /**
     * Subscribe to the $deviceType$ events queue returning a source of events. We always start at the tail of
     * a queue, relying on external processes to cleanup the tail and therefore, the length of the queue, sane.
     * Our goal is only ever output the most recent events so that we do not flood the UI. We thus reduce the
     * events noting the last one received and then stream subsequent ones. Given the power of Akka streams
     * we could get much fancier too e.g. throttling.
     *
     * Note that given the reduction of values we must group a bounded number of sensors, which is passed in
     * as a parameter. What this means is that we show initial data for up to this parameter's value. If there
     * are more sensors than this bounds then their readings won't be shown until new readings come in. In a
     * healthy system, log compaction should also take care of cleaning up readings for sensors no longer
     * in use. All this said, if maxInitialSensors is sized correctly then there should be no practical issue.
     */
    static Source<Tuple2<$deviceType;format="Camel"$Reading, Long>, NotUsed> events(DurableQueue durableQueue,
                                                                  int maxInitialSensors,
                                                                  Function<String, CompletionStage<Either<Principal.FailureResponse, Principal.SecretRetrieved>>> getSecret,
                                                                  Materializer mat) {
        return Source.fromSourceCompletionStage(
                durableQueue.source($deviceType;format="Camel"$Reading.DATA_UP_JSON_TOPIC, Option.empty(), true).<DurableQueue.Received, NotUsed>asJava()
                        .via($deviceType;format="Camel"$Reading.tailer(getSecret, mat.executionContext()))
                        .groupBy(maxInitialSensors, e -> e._1.getNwkAddr())
                        .reduce((e0, e1) -> e1)
                        .mergeSubstreams()
                        .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider()))
                        .runWith(Sink.seq(), mat)
                        .thenApply(lastEvents -> {
                            OptionalLong lastOffset = (lastEvents.isEmpty() ? OptionalLong.empty() : OptionalLong.of(lastEvents.get(lastEvents.size() - 1)._2));
                            return Source
                                    .from(lastEvents)
                                    .concat(
                                            durableQueue.source($deviceType;format="Camel"$Reading.DATA_UP_JSON_TOPIC, OptionConverters.toScala(lastOffset)).<DurableQueue.Received, NotUsed>asJava()
                                                    .dropWhile(r -> lastOffset.isPresent() && lastOffset.getAsLong() == r.offset()) // offsets are inclusive, so skip one if we have one
                                                    .via($deviceType;format="Camel"$Reading.tailer(getSecret, mat.executionContext())));
                        })
        ).mapMaterializedValue(me -> NotUsed.notUsed());
    }
}
