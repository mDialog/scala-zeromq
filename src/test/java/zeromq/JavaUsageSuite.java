package zeromq;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.testkit.SocketUtil;
import akka.util.ByteString;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * Test that we can successfully use the library from java
 */
public class JavaUsageSuite {
    static ActorSystem system;
    static ZeroMQExtension zmq;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
        zmq = new ZeroMQExtension(system);
    }

    @AfterClass
    public static void teardown() {
        JavaTestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void JavaAPI() {
        new JavaTestKit(system) {{
            String endpoint = "tcp:/" + SocketUtil.temporaryServerAddress(SocketUtil.temporaryServerAddress$default$1(), SocketUtil.temporaryServerAddress$default$2());
            ActorRef publisher = zmq.newSocketJ(SocketType.Pub$.MODULE$, new Bind(endpoint));
            ActorRef subscriber = zmq.newSocketJ(SocketType.Sub$.MODULE$, new Listener(getRef()), new Connect(endpoint), package$.MODULE$.SubscribeAll());

            publisher.tell(Message$.MODULE$.apply(ByteString.fromString("hello world")), ActorRef.noSender());
            assertThat(expectMsgClass(FiniteDuration.apply(10, TimeUnit.SECONDS), Message.class).apply(0).utf8String(), equalTo("hello world"));

            system.stop(subscriber);
            system.stop(publisher);
        }};
    }
}
