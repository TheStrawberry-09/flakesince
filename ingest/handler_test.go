package main

import (
	"bytes"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func TestHealth(t *testing.T) {
	rec := httptest.NewRecorder()
	newMux().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/healthz", nil))

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusOK)
	}
}

func TestIngestAcceptsBody(t *testing.T) {
	rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodPost, "/ingest", strings.NewReader(
    		`<testsuite name="Demo"><testcase classname="Demo" name="works()" time="0.001"/></testsuite>`))
	newMux().ServeHTTP(rec, req)

	if rec.Code != http.StatusAccepted {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusAccepted)
	}
}

func TestIngestRejectsTooLargeBody(t *testing.T) {
	tooBig := bytes.Repeat([]byte("a"), maxBodyBytes+1)
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ingest", bytes.NewReader(tooBig))
	newMux().ServeHTTP(rec, req)

	if rec.Code != http.StatusRequestEntityTooLarge {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusRequestEntityTooLarge)
	}
}

func TestIngestOnlyAcceptsPost(t *testing.T) {
	rec := httptest.NewRecorder()
	newMux().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/ingest", nil))

	if rec.Code != http.StatusMethodNotAllowed {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusMethodNotAllowed)
	}
}
func TestIngestRejectsInvalidXml(t *testing.T) {
	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/ingest", strings.NewReader("<html></html>"))
	newMux().ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}