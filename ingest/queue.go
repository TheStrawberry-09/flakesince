package main

import (
	"context"
	"fmt"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/service/sqs"
)

// maxMessageBytes is the message size that both Amazon SQS and ElasticMQ accept.
const maxMessageBytes = 256 * 1024

// RunMessage is what the Spring API reads from the queue. The JSON names match RunUploadRequest.
type RunMessage struct {
	ProjectID     int64     `json:"projectId"`
	CommitSha     string    `json:"commitSha"`
	Branch        string    `json:"branch"`
	ProviderRunID string    `json:"providerRunId"`
	RunAttempt    int       `json:"runAttempt"`
	StartedAt     time.Time `json:"startedAt"`
	Results       []Result  `json:"results"`
}

// Publisher sends one message body to the queue.
type Publisher interface {
	Publish(ctx context.Context, body []byte) error
}

type sqsPublisher struct {
	client   *sqs.Client
	queueURL string
}

func newSQSPublisher(ctx context.Context, queueName string) (*sqsPublisher, error) {
	cfg, err := config.LoadDefaultConfig(ctx)
	if err != nil {
		return nil, fmt.Errorf("loading AWS settings: %w", err)
	}
	client := sqs.NewFromConfig(cfg)

	out, err := client.GetQueueUrl(ctx, &sqs.GetQueueUrlInput{QueueName: aws.String(queueName)})
	if err != nil {
		return nil, fmt.Errorf("finding queue %q: %w", queueName, err)
	}
	return &sqsPublisher{client: client, queueURL: aws.ToString(out.QueueUrl)}, nil
}

func (p *sqsPublisher) Publish(ctx context.Context, body []byte) error {
	_, err := p.client.SendMessage(ctx, &sqs.SendMessageInput{
		QueueUrl:    aws.String(p.queueURL),
		MessageBody: aws.String(string(body)),
	})
	return err
}