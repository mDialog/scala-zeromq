package zeromq;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.JavaTestKit;
import akka.util.ByteString;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static scala.collection.JavaConversions.asScalaBuffer;

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
            String endpoint = TestUtils.getEndpoint();
            ActorRef publisher = zmq.newSocket(SocketType.Pub$.MODULE$, asScalaBuffer(Arrays.asList(new Bind(endpoint))), null);
            ActorRef subscriber = zmq.newSocket(SocketType.Sub$.MODULE$, asScalaBuffer(Arrays.asList(new Listener(getRef()), new Connect(endpoint), package$.MODULE$.SubscribeAll())), null);

            publisher.tell(new Message(asScalaBuffer(Arrays.asList(ByteString.fromString("hello world")))), ActorRef.noSender());
            assertThat(expectMsgClass(Message.class).apply(0).utf8String(), equalTo("hello world"));

            system.stop(subscriber);
            system.stop(publisher);
        }};
    }
}
