package main

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

const validXML = `<testsuite name="Demo"><testcase classname="Demo" name="works()" time="0.001"/></testsuite>`

var fixedNow = time.Date(2026, 10, 24, 9, 0, 0, 0, time.UTC)

type brokenChecker struct{}

func (brokenChecker) Check(context.Context, string) (int64, error) {
	return 0, errors.New("database is down")
}

type fakePublisher struct {
	bodies [][]byte
	err    error
}

func (f *fakePublisher) Publish(_ context.Context, body []byte) error {
	if f.err != nil {
		return f.err
	}
	f.bodies = append(f.bodies, body)
	return nil
}

func goodKeys() KeyChecker {
	return &fakeChecker{projects: map[string]int64{"fs_live_good": 7}}
}

func serve(keys KeyChecker, queue Publisher, req *http.Request) *httptest.ResponseRecorder {
	a := &app{keys: keys, queue: queue, now: func() time.Time { return fixedNow }}
	rec := httptest.NewRecorder()
	newMux(a).ServeHTTP(rec, req)
	return rec
}

func upload(body io.Reader, key string) *http.Request {
	req := httptest.NewRequest(http.MethodPost, "/ingest", body)
	if key != "" {
		req.Header.Set("Authorization", "Bearer "+key)
	}
	req.Header.Set("X-Commit-Sha", "abc123")
	req.Header.Set("X-Branch", "main")
	req.Header.Set("X-Run-Id", "1000")
	req.Header.Set("X-Run-Attempt", "2")
	return req
}

func withoutHeader(req *http.Request, name string) *http.Request {
	req.Header.Del(name)
	return req
}

func withHeader(req *http.Request, name, value string) *http.Request {
	req.Header.Set(name, value)
	return req
}

func tooManyResults() string {
	var b strings.Builder
	b.WriteString(`<testsuite name="Big">`)
	for i := range 40 {
		fmt.Fprintf(&b, `<testcase classname="Big" name="t%d()"><failure message="%s"/></testcase>`, i, strings.Repeat("x", 9000))
	}
	b.WriteString(`</testsuite>`)
	return b.String()
}

func TestIngestStatusCodes(t *testing.T) {
	tests := []struct {
		name  string
		keys  KeyChecker
		queue *fakePublisher
		req   *http.Request
		want  int
	}{
		{"valid upload", goodKeys(), &fakePublisher{}, upload(strings.NewReader(validXML), "fs_live_good"), http.StatusAccepted},
		{"missing key", goodKeys(), &fakePublisher{}, upload(strings.NewReader(validXML), ""), http.StatusUnauthorized},
		{"wrong key", goodKeys(), &fakePublisher{}, upload(strings.NewReader(validXML), "fs_live_wrong"), http.StatusUnauthorized},
		{"key check fails", brokenChecker{}, &fakePublisher{}, upload(strings.NewReader(validXML), "fs_live_good"), http.StatusServiceUnavailable},
		{"missing commit header", goodKeys(), &fakePublisher{}, withoutHeader(upload(strings.NewReader(validXML), "fs_live_good"), "X-Commit-Sha"), http.StatusBadRequest},
		{"bad run attempt", goodKeys(), &fakePublisher{}, withHeader(upload(strings.NewReader(validXML), "fs_live_good"), "X-Run-Attempt", "zero"), http.StatusBadRequest},
		{"bad start time", goodKeys(), &fakePublisher{}, withHeader(upload(strings.NewReader(validXML), "fs_live_good"), "X-Started-At", "yesterday"), http.StatusBadRequest},
		{"invalid xml", goodKeys(), &fakePublisher{}, upload(strings.NewReader("<html></html>"), "fs_live_good"), http.StatusBadRequest},
		{"too large body", goodKeys(), &fakePublisher{}, upload(bytes.NewReader(bytes.Repeat([]byte("a"), maxBodyBytes+1)), "fs_live_good"), http.StatusRequestEntityTooLarge},
		{"too large message", goodKeys(), &fakePublisher{}, upload(strings.NewReader(tooManyResults()), "fs_live_good"), http.StatusRequestEntityTooLarge},
		{"queue fails", goodKeys(), &fakePublisher{err: errors.New("queue is down")}, upload(strings.NewReader(validXML), "fs_live_good"), http.StatusServiceUnavailable},
		{"wrong method", goodKeys(), &fakePublisher{}, httptest.NewRequest(http.MethodGet, "/ingest", nil), http.StatusMethodNotAllowed},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			rec := serve(tt.keys, tt.queue, tt.req)
			if rec.Code != tt.want {
				t.Errorf("status = %d, want %d (body: %s)", rec.Code, tt.want, rec.Body.String())
			}
			if tt.want != http.StatusAccepted && len(tt.queue.bodies) != 0 {
				t.Errorf("a rejected upload was still sent to the queue")
			}
		})
	}
}

func TestValidUploadSendsOneMessage(t *testing.T) {
	queue := &fakePublisher{}
	rec := serve(goodKeys(), queue, upload(strings.NewReader(validXML), "fs_live_good"))
	if rec.Code != http.StatusAccepted {
		t.Fatalf("status = %d, body = %s", rec.Code, rec.Body.String())
	}
	if len(queue.bodies) != 1 {
		t.Fatalf("messages sent = %d, want 1", len(queue.bodies))
	}

	var got RunMessage
	if err := json.Unmarshal(queue.bodies[0], &got); err != nil {
		t.Fatal(err)
	}
	if got.ProjectID != 7 || got.CommitSha != "abc123" || got.Branch != "main" ||
		got.ProviderRunID != "1000" || got.RunAttempt != 2 || !got.StartedAt.Equal(fixedNow) {
		t.Errorf("message = %+v", got)
	}
	if len(got.Results) != 1 || got.Results[0].Name != "works()" || got.Results[0].Status != "PASSED" {
		t.Errorf("results = %+v", got.Results)
	}
}

func TestStartedAtHeaderIsUsed(t *testing.T) {
	queue := &fakePublisher{}
	req := withHeader(upload(strings.NewReader(validXML), "fs_live_good"), "X-Started-At", "2026-10-24T16:30:00+07:00")
	serve(goodKeys(), queue, req)

	var got RunMessage
	if err := json.Unmarshal(queue.bodies[0], &got); err != nil {
		t.Fatal(err)
	}
	want := time.Date(2026, 10, 24, 9, 30, 0, 0, time.UTC)
	if !got.StartedAt.Equal(want) {
		t.Errorf("startedAt = %v, want %v", got.StartedAt, want)
	}
}

func TestHealth(t *testing.T) {
	rec := serve(goodKeys(), &fakePublisher{}, httptest.NewRequest(http.MethodGet, "/healthz", nil))
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusOK)
	}
}