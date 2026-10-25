package com.thanawat.flakesince.run;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "flakesince.sqs.enabled", havingValue = "true")
public class RunQueueListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RunQueueListener.class);

    private final SqsClient sqs;
    private final RunIngestService ingestService;
    private final JsonMapper json;
    private final String queueName;

    private volatile boolean running;
    private Thread worker;
    private String queueUrl;

    public RunQueueListener(SqsClient sqs, RunIngestService ingestService, JsonMapper json,
                            @Value("${flakesince.sqs.queue-name}") String queueName) {
        this.sqs = sqs;
        this.ingestService = ingestService;
        this.json = json;
        this.queueName = queueName;
    }

    @Override
    public void start() {
        queueUrl = sqs.getQueueUrl(r -> r.queueName(queueName)).queueUrl();
        running = true;
        worker = Thread.ofVirtual().name("run-queue-listener").start(this::pollLoop);
        log.info("Listening for runs on queue {}", queueName);
    }

    @Override
    public void stop() {
        running = false;
        try {
            worker.join(Duration.ofSeconds(15));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void pollLoop() {
        while (running) {
            try {
                var messages = sqs.receiveMessage(r -> r
                        .queueUrl(queueUrl)
                        .maxNumberOfMessages(10)
                        .waitTimeSeconds(10)).messages();
                for (Message message : messages) {
                    handle(message);
                }
            } catch (Exception e) {
                if (!running) {
                    break;
                }
                log.warn("Could not read from the queue, retrying in 5 seconds", e);
                pause(Duration.ofSeconds(5));
            }
        }
    }

    void handle(Message message) {
        try {
            RunUploadRequest run = json.readValue(message.body(), RunUploadRequest.class);
            IngestResult result = ingestService.ingest(run);
            sqs.deleteMessage(r -> r.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            log.info("Saved run {} attempt {} (duplicate={}, results={})",
                    run.providerRunId(), run.runAttempt(), result.duplicate(), result.resultsSaved());
        } catch (Exception e) {
            log.error("Could not save message {}; SQS will retry it", message.messageId(), e);
        }
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}