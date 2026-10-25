package main

import (
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"
)

// runMeta is the information about a CI run that is not inside the JUnit XML.
type runMeta struct {
	commitSha     string
	branch        string
	providerRunID string
	runAttempt    int
	startedAt     time.Time
}

func readRunMeta(r *http.Request, now time.Time) (runMeta, error) {
	m := runMeta{
		commitSha:     strings.TrimSpace(r.Header.Get("X-Commit-Sha")),
		branch:        strings.TrimSpace(r.Header.Get("X-Branch")),
		providerRunID: strings.TrimSpace(r.Header.Get("X-Run-Id")),
		runAttempt:    1,
		startedAt:     now.UTC(),
	}

	if m.commitSha == "" || m.branch == "" || m.providerRunID == "" {
		return runMeta{}, errors.New("the headers X-Commit-Sha, X-Branch and X-Run-Id are required")
	}
	if len(m.commitSha) > 64 || len(m.branch) > 255 || len(m.providerRunID) > 100 {
		return runMeta{}, errors.New("X-Commit-Sha, X-Branch or X-Run-Id is too long")
	}

	if value := r.Header.Get("X-Run-Attempt"); value != "" {
		attempt, err := strconv.Atoi(value)
		if err != nil || attempt < 1 {
			return runMeta{}, errors.New("X-Run-Attempt must be a whole number of 1 or more")
		}
		m.runAttempt = attempt
	}

	if value := r.Header.Get("X-Started-At"); value != "" {
		startedAt, err := time.Parse(time.RFC3339, value)
		if err != nil {
			return runMeta{}, errors.New("X-Started-At must be a time like 2026-10-24T09:00:00Z")
		}
		m.startedAt = startedAt.UTC()
	}

	return m, nil
}