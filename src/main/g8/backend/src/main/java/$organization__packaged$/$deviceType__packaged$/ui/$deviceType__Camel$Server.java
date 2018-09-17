package $organization;format="package"$.$deviceType;format="camel"$.ui;

import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.marshalling.sse.EventStreamMarshalling;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import akka.http.scaladsl.model.sse.ServerSentEvent;
import akka.japi.pf.PFBuilder;
import akka.stream.Materializer;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import $organization;format="package"$.$deviceType;format="camel"$.transformer.$deviceType;format="Camel"$Transformer;
import $organization;format="package"$.$deviceType;format="camel"$.ui.model.Credentials;
import com.cisco.streambed.Application;
import com.cisco.streambed.ApplicationContext;
import com.cisco.streambed.durablequeue.DurableQueue;
import com.cisco.streambed.durablequeue.chroniclequeue.ChronicleQueue;
import com.cisco.streambed.durablequeue.chroniclequeue.DurableQueueProvider;
import com.cisco.streambed.http.HttpServerConfig;
import com.cisco.streambed.http.identity.UserIdentityService;
import com.cisco.streambed.identity.Principal;
import com.cisco.streambed.identity.iox.IOxSecretStore;
import com.cisco.streambed.identity.iox.SecretStoreProvider;
import com.cisco.streambed.storage.fs.FileSystemRawStorage;
import com.cisco.streambed.storage.fs.RawStorageProvider;
import com.cisco.streambed.tracing.jaeger.TracerConfig;
import com.typesafe.config.Config;
import io.opentracing.Tracer;
import scala.compat.java8.FutureConverters;
import scala.compat.java8.OptionConverters;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionStage;

/**
 * This is our main entry point to the application being responsible for serving assets as well as providings the UI's
 * RESTful endpoints
 */
public class $deviceType;format="Camel"$Server extends Application implements DurableQueueProvider, RawStorageProvider, SecretStoreProvider {

    @Override
    public void main(String[] args, ApplicationContext context) {
        ActorSystem system = context.system();
        Materializer mat = context.mat();
        LoggingAdapter log = Logging.getLogger(system, this);

        // In order to access all directives we need an instance where the routes are define.
        Routes routes = new Routes();

        final Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = routes.createMainRoute(context).flow(system, mat);

        HttpServerConfig
                .bindAndHandle(context, routeFlow)
                .thenApply(serverBinding -> {
                    log.info("Server listening on {}", serverBinding.localAddress());

                    return serverBinding;
                })
                .exceptionally(failure -> {
                    log.error(failure, "Bind failed, exiting");
                    System.exit(1);
                    return null;
                });

        // Start the transformer up as we are running it within the same process
        // It can be factored out into its own process if required.
        Tracer tracer = TracerConfig.tracer(context.config(), context.system());
        $deviceType;format="Camel"$Transformer
                .source(context.durableQueue(), Principal.toJava(context.getSecret()), tracer, mat)
                .runWith(Sink.ignore(), mat);
    }

    // Wire in Chronicle Queue as our durable queue
    @Override
    public ChronicleQueue acquireDurableQueue(Config config, Materializer mat, ActorSystem system) {
        return DurableQueueProvider.super.acquireDurableQueue(config, mat, system);
    }

    // Wire in the IOx secret store as our secret store
    @Override
    public IOxSecretStore acquireSecretStore(Config config, Materializer mat, ActorSystem system) {
        return SecretStoreProvider.super.acquireSecretStore(config, mat, system);
    }

    // Wire in fs as our raw storage
    @Override
    public FileSystemRawStorage acquireRawStorage(Config config, Materializer mat, ActorSystem system) {
        return RawStorageProvider.super.acquireRawStorage(config, mat, system);
    }

    // Describe the HTTP routes
    private class Routes extends AllDirectives {
        private Route createMainRoute(ApplicationContext context) {

            ActorSystem system = context.system();
            Materializer mat = context.mat();
            DurableQueue durableQueue = context.durableQueue();
            UserIdentityService identityService = UserIdentityService.apply(context, system);

            int maxInitialSensors = system.settings().config().getInt("$deviceType;format="norm"$.max-initial-sensors");
            return route(
                    path(PathMatchers.segment("api").slash("login"), () ->
                            post(() ->
                                    entity(Jackson.unmarshaller(Credentials.class), credentials -> {
                                        CompletionStage<String> result = FutureConverters
                                                .toJava(identityService.authenticate(credentials.getUsername(), credentials.getPassword()))
                                                .thenApply(r -> OptionConverters.toJava(r).orElseThrow(RuntimeException::new));
                                        return onComplete(result, maybeResult ->
                                                maybeResult
                                                        .map(this::complete)
                                                        .recover(new PFBuilder<Throwable, Route>()
                                                                .matchAny(ex -> complete(StatusCodes.UNAUTHORIZED, "Bad credentials"))
                                                                .build())
                                                        .get());
                                    }))),

                    pathPrefix("api", () ->
                            authenticateOAuth2Async("secured api", identityService::verifier, principal -> route(
                                    path("end-devices", () -> route(

                                            get(() ->
                                                    completeOK(
                                                            EndDeviceService.events(
                                                                    durableQueue,
                                                                    maxInitialSensors,
                                                                    Principal.toJava(principal.getSecret()),
                                                                    mat)
                                                                    .map(t -> ServerSentEventMarshaller.toServerSentEvent(t._1(), t._2()))
                                                                    .keepAlive(Duration.of(10, ChronoUnit.SECONDS), ServerSentEvent::heartbeat),
                                                            EventStreamMarshalling.toEventStream())))),
                                    path("$deviceType;format="norm"$", () -> route(

                                            get(() ->
                                                    completeOK(
                                                            $deviceType;format="Camel"$Service.events(
                                                                    durableQueue,
                                                                    maxInitialSensors,
                                                                    Principal.toJava(principal.getSecret()),
                                                                    mat)
                                                                    .map(t -> ServerSentEventMarshaller.toServerSentEvent(t._1(), t._2()))
                                                                    .keepAlive(Duration.of(10, ChronoUnit.SECONDS), ServerSentEvent::heartbeat),
                                                            EventStreamMarshalling.toEventStream()))))))),

                    // Explicitly list out all SPA routes so that refreshing works as expected

                    pathEndOrSingleSlash(() -> getFromResource("dist/index.html")),

                    getFromResourceDirectory("dist")
            ).seal();
        }
    }
}
