package main

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
)

const maxBodyBytes = 10 << 20 // 10 MB

func handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "UP"})
}

func handleIngest(w http.ResponseWriter, r *http.Request) {
	r.Body = http.MaxBytesReader(w, r.Body, maxBodyBytes)

	body, err := io.ReadAll(r.Body)
	if err != nil {
		var tooLarge *http.MaxBytesError
		if errors.As(err, &tooLarge) {
			writeJSON(w, http.StatusRequestEntityTooLarge, map[string]string{"error": "body is larger than 10 MB"})
			return
		}
		writeJSON(w, http.StatusBadRequest, map[string]string{"error": "could not read the request body"})
		return
	}

	writeJSON(w, http.StatusAccepted, map[string]int{"bytes": len(body)})
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}