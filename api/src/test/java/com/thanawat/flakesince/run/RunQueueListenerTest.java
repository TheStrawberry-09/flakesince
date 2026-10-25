package com.thanawat.flakesince.run;

import com.thanawat.flakesince.TestcontainersConfiguration;
import com.thanawat.flakesince.project.Project;
import com.thanawat.flakesince.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "flakesince.sqs.enabled=true")
@Import(TestcontainersConfiguration.class)
@Testcontainers
class RunQueueListenerTest {

    @Container
    static GenericContainer<?> elasticMq = new GenericContainer<>(DockerImageName.parse("softwaremill/elasticmq-native:1.7.1"))
            .withExposedPorts(9324)
            .withCopyFileToContainer(MountableFile.forHostPath("../deploy/local/elasticmq.conf"), "/opt/elasticmq.conf");

    @DynamicPropertySource
    static void queueEndpoint(DynamicPropertyRegistry registry) {
        registry.add("flakesince.sqs.endpoint",
                () -> "http://" + elasticMq.getHost() + ":" + elasticMq.getMappedPort(9324));
    }

    @Autowired SqsClient sqs;
    @Autowired ProjectRepository projects;
    @Autowired CiRunRepository runs;

    @Test
    void savesRunFromQueueAndDeletesMessage() throws Exception {
        Project project = projects.save(new Project("Queue Test", "queue-test"));
        String queueUrl = sqs.getQueueUrl(r -> r.queueName("flakesince-runs")).queueUrl();

        sqs.sendMessage(r -> r.queueUrl(queueUrl).messageBody("""
                {"projectId": %d, "commitSha": "abc123", "branch": "main",
                 "providerRunId": "q-1", "runAttempt": 1, "startedAt": "2026-10-25T09:00:00Z",
                 "results": [{"suite": "Demo", "name": "works()", "status": "PASSED", "durationMs": 3}]}
                """.formatted(project.getId())));

        waitUntil(() -> runs.existsByProjectIdAndProviderRunIdAndRunAttempt(project.getId(), "q-1", 1));
        waitUntil(() -> queueIsEmpty(queueUrl));
    }

    private boolean queueIsEmpty(String queueUrl) {
        var attributes = sqs.getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNames(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)).attributes();
        return "0".equals(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
                && "0".equals(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        assertTrue(condition.getAsBoolean(), "condition was not met within 20 seconds");
    }
}