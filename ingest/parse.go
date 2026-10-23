package main

import (
	"bytes"
	"encoding/xml"
	"errors"
	"fmt"
	"io"
	"math"
	"strconv"
	"strings"
)

const (
	maxMessageRunes = 10_000
	maxNameRunes    = 500
)

// Result has the same fields as TestResultUpload in the Spring API.
type Result struct {
	Suite          string `json:"suite"`
	Name           string `json:"name"`
	Status         string `json:"status"`
	DurationMs     int    `json:"durationMs"`
	FailureMessage string `json:"failureMessage,omitempty"`
}

type xmlSuites struct {
	Suites []xmlSuite `xml:"testsuite"`
}

type xmlSuite struct {
	Name   string     `xml:"name,attr"`
	Cases  []xmlCase  `xml:"testcase"`
	Suites []xmlSuite `xml:"testsuite"`
}

type xmlCase struct {
	Name      string      `xml:"name,attr"`
	ClassName string      `xml:"classname,attr"`
	Time      string      `xml:"time,attr"`
	Failure   *xmlProblem `xml:"failure"`
	Error     *xmlProblem `xml:"error"`
	Skipped   *xmlProblem `xml:"skipped"`
}

type xmlProblem struct {
	Message string `xml:"message,attr"`
	Text    string `xml:",chardata"`
}

// ParseJUnit reads a JUnit XML document with either <testsuites> or <testsuite> as its root.
func ParseJUnit(data []byte) ([]Result, error) {
	root, err := rootElement(data)
	if err != nil {
		return nil, err
	}

	var suites []xmlSuite
	switch root {
	case "testsuites":
		var doc xmlSuites
		if err := xml.Unmarshal(data, &doc); err != nil {
			return nil, fmt.Errorf("invalid JUnit XML: %w", err)
		}
		suites = doc.Suites
	case "testsuite":
		var suite xmlSuite
		if err := xml.Unmarshal(data, &suite); err != nil {
			return nil, fmt.Errorf("invalid JUnit XML: %w", err)
		}
		suites = []xmlSuite{suite}
	default:
		return nil, fmt.Errorf("unexpected root element <%s>, want <testsuites> or <testsuite>", root)
	}

	var results []Result
	for _, suite := range suites {
		results = appendSuite(results, suite)
	}
	if len(results) == 0 {
		return nil, errors.New("no test cases found")
	}
	return results, nil
}

func rootElement(data []byte) (string, error) {
	decoder := xml.NewDecoder(bytes.NewReader(data))
	for {
		token, err := decoder.Token()
		if errors.Is(err, io.EOF) {
			return "", errors.New("empty XML document")
		}
		if err != nil {
			return "", fmt.Errorf("invalid JUnit XML: %w", err)
		}
		if start, ok := token.(xml.StartElement); ok {
			return start.Name.Local, nil
		}
	}
}

func appendSuite(results []Result, suite xmlSuite) []Result {
	for _, c := range suite.Cases {
		if strings.TrimSpace(c.Name) == "" {
			continue
		}
		results = append(results, toResult(suite.Name, c))
	}
	for _, nested := range suite.Suites {
		results = appendSuite(results, nested)
	}
	return results
}

func toResult(suiteName string, c xmlCase) Result {
	suite := c.ClassName
	if suite == "" {
		suite = suiteName
	}

	result := Result{
		Suite:      truncate(suite, maxNameRunes),
		Name:       truncate(c.Name, maxNameRunes),
		Status:     "PASSED",
		DurationMs: durationMs(c.Time),
	}

	switch {
	case c.Failure != nil:
		result.Status = "FAILED"
		result.FailureMessage = failureMessage(c.Failure)
	case c.Error != nil:
		result.Status = "ERROR"
		result.FailureMessage = failureMessage(c.Error)
	case c.Skipped != nil:
		result.Status = "SKIPPED"
	}
	return result
}

func durationMs(seconds string) int {
	value, err := strconv.ParseFloat(strings.ReplaceAll(seconds, ",", ""), 64)
	if err != nil || value < 0 {
		return 0
	}
	return int(math.Round(value * 1000))
}

func failureMessage(p *xmlProblem) string {
	message := strings.TrimSpace(p.Message)
	text := strings.TrimSpace(p.Text)

	var out string
	switch {
	case text == "":
		out = message
	case message == "" || strings.HasPrefix(text, message):
		out = text
	default:
		out = message + "\n" + text
	}
	return truncate(out, maxMessageRunes)
}

func truncate(s string, max int) string {
	runes := []rune(s)
	if len(runes) <= max {
		return s
	}
	return string(runes[:max])
}