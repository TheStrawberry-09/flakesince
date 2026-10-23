package main

import (
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"strings"
)

const maxBodyBytes = 10 << 20 // 10 MB

type app struct {
	keys KeyChecker
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

	results, err := ParseJUnit(body)
	if err != nil {
		writeJSON(w, http.StatusBadRequest, errorBody(err.Error()))
		return
	}

	writeJSON(w, http.StatusAccepted, map[string]any{"projectId": projectID, "results": len(results)})
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