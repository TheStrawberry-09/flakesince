package main

import (
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"
)

const maxBodyBytes = 10 << 20 // 10 MB

type app struct {
	keys  KeyChecker
	queue Publisher
	now   func() time.Time
}

func newMux(a *app) *http.ServeMux {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", handleHealth)
	mux.HandleFunc("POST /ingest", a.handleIngest)
	return mux
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "UP"})
}

func (a *app) handleIngest(w http.ResponseWriter, r *http.Request) {
	// 1. Check the API key.
	key, ok := bearerToken(r)
	if !ok {
		unauthorized(w, "missing API key: send the header Authorization: Bearer <key>")
		return
	}

	projectID, err := a.keys.Check(r.Context(), key)
	if errors.Is(err, ErrInvalidKey) {
		unauthorized(w, "invalid or revoked API key")
		return
	}
	if err != nil {
		slog.Error("API key check failed", "error", err)
		writeJSON(w, http.StatusServiceUnavailable, errorBody("could not check the API key, please try again"))
		return
	}

	// 2. Read the run headers (commit, branch, run ID, attempt). NEW in step 23.
	meta, err := readRunMeta(r, a.now())
	if err != nil {
		writeJSON(w, http.StatusBadRequest, errorBody(err.Error()))
		return
	}

	// 3. Read the body, at most 10 MB.
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)
	body, err := io.ReadAll(r.Body)
	if err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			writeJSON(w, http.StatusRequestEntityTooLarge, errorBody("body is larger than 10 MB"))
			return
		}
		writeJSON(w, http.StatusBadRequest, errorBody("could not read the request body"))
		return
	}

	// 4. Parse the JUnit XML.
	results, err := ParseJUnit(body)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, errorBody(err.Error()))
		return
	}

	// 5. Build the queue message and check its size. NEW in step 23.
	message, err := json.Marshal(RunMessage{
		ProjectID:     projectID,
		CommitSha:     meta.commitSha,
		Branch:        meta.branch,
		ProviderRunID: meta.providerRunID,
		RunAttempt:    meta.runAttempt,
		StartedAt:     meta.startedAt,
		Results:       results,
	})
	if err != nil {
		slog.Error("encoding run message failed", "error", err)
		writeJSON(w, http.StatusInternalServerError, errorBody("could not prepare the run"))
		return
	}
	if len(message) > maxMessageBytes {
		writeJSON(w, http.StatusRequestEntityTooLarge, errorBody("too many or too long test results for one upload (256 KB after parsing); split the upload"))
		return
	}

	// 6. Send it to the queue. NEW in step 23.
	if err := a.queue.Publish(r.Context(), message); err != nil {
		slog.Error("sending run to the queue failed", "error", err)
		writeJSON(w, http.StatusServiceUnavailable, errorBody("could not queue the run, please try again"))
		return
	}

	writeJSON(w, http.StatusAccepted, map[string]any{"projectId": projectID, "results": len(results), "queued": true})
}

func bearerToken(r *http.Request) (string, bool) {
	token, found := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
	token = strings.TrimSpace(token)
	return token, found && token != ""
}

func unauthorized(w http.ResponseWriter, message string) {
	w.Header().Set("WWW-Authenticate", "Bearer")
	writeJSON(w, http.StatusUnauthorized, errorBody(message))
}

func errorBody(message string) map[string]string {
	return map[string]string{"error": message}
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}