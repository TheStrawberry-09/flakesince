package main

import (
	"bytes"
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

const validXML = `<testsuite name="Demo"><testcase classname="Demo" name="works()" time="0.001"/></testsuite>`

type brokenChecker struct{}

func (brokenChecker) Check(context.Context, string) (int64, error) {
	return 0, errors.New("database is down")
}

func testMux(keys KeyChecker) *http.ServeMux {
	return newMux(&app{keys: keys})
}

func goodKeys() KeyChecker {
	return &fakeChecker{projects: map[string]int64{"fs_live_good": 7}}
}

func upload(body io.Reader, key string) *http.Request {
	req := httptest.NewRequest(http.MethodPost, "/ingest", body)
	if key != "" {
		req.Header.Set("Authorization", "Bearer "+key)
	}
	return req
}

func TestIngestStatusCodes(t *testing.T) {
	tests := []struct {
		name string
		keys KeyChecker
		req  *http.Request
		want int
	}{
		{"valid upload", goodKeys(), upload(strings.NewReader(validXML), "fs_live_good"), http.StatusAccepted},
		{"missing key", goodKeys(), upload(strings.NewReader(validXML), ""), http.StatusUnauthorized},
		{"wrong key", goodKeys(), upload(strings.NewReader(validXML), "fs_live_wrong"), http.StatusUnauthorized},
		{"key check fails", brokenChecker{}, upload(strings.NewReader(validXML), "fs_live_good"), http.StatusServiceUnavailable},
		{"invalid xml", goodKeys(), upload(strings.NewReader("<html></html>"), "fs_live_good"), http.StatusBadRequest},
		{"too large", goodKeys(), upload(bytes.NewReader(bytes.Repeat([]byte("a"), maxBodyBytes+1)), "fs_live_good"), http.StatusRequestEntityTooLarge},
		{"wrong method", goodKeys(), httptest.NewRequest(http.MethodGet, "/ingest", nil), http.StatusMethodNotAllowed},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			rec := httptest.NewRecorder()
			testMux(tt.keys).ServeHTTP(rec, tt.req)
			if rec.Code != tt.want {
				t.Errorf("status = %d, want %d (body: %s)", rec.Code, tt.want, rec.Body.String())
			}
		})
	}
}

func TestValidUploadReturnsProjectFromKey(t *testing.T) {
	rec := httptest.NewRecorder()
	testMux(goodKeys()).ServeHTTP(rec, upload(strings.NewReader(validXML), "fs_live_good"))

	if !strings.Contains(rec.Body.String(), `"projectId":7`) {
		t.Errorf("body = %s, want projectId 7", rec.Body.String())
	}
}

func TestHealth(t *testing.T) {
	rec := httptest.NewRecorder()
	testMux(goodKeys()).ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/healthz", nil))
	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusOK)
	}
}