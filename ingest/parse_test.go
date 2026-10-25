package main

import (
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
)

type row struct {
	suite, name, status string
	ms                  int
}

func rows(results []Result) []row {
	out := make([]row, 0, len(results))
	for _, r := range results {
		out = append(out, row{r.Suite, r.Name, r.Status, r.DurationMs})
	}
	return out
}

func TestParseJUnit(t *testing.T) {
	const demo = "com.thanawat.flaky_demo.FlakyDemoApplicationTests"

	tests := []struct {
		name            string
		file            string
		want            []row
		messageContains string
	}{
		{
			name: "gradle",
			file: "gradle.xml",
			want: []row{
				{demo, "flakyRandomTest()", "FAILED", 423},
				{demo, "uppercaseWorks()", "PASSED", 2},
				{demo, "additionWorks()", "PASSED", 1},
				{demo, "contextLoads()", "PASSED", 1},
			},
			messageContains: "FlakyDemoApplicationTests.java:26",
		},
		{
			name: "pytest",
			file: "pytest.xml",
			want: []row{
				{"tests.test_math", "test_add", "PASSED", 1},
				{"tests.test_math", "test_flaky", "FAILED", 2},
				{"tests.test_math", "test_later", "SKIPPED", 0},
			},
			messageContains: "assert 0.21 > 0.3",
		},
		{
			name: "go-junit-report",
			file: "go-junit-report.xml",
			want: []row{
				{"example.com/calc", "TestAdd", "PASSED", 0},
				{"example.com/calc", "TestTimeout", "FAILED", 250},
			},
			messageContains: "took 250ms",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			data, err := os.ReadFile(filepath.Join("testdata", tt.file))
			if err != nil {
				t.Fatal(err)
			}

			got, err := ParseJUnit(data)
			if err != nil {
				t.Fatalf("ParseJUnit returned error: %v", err)
			}

			if !reflect.DeepEqual(rows(got), tt.want) {
				t.Errorf("results:\n got  %+v\n want %+v", rows(got), tt.want)
			}

			for _, r := range got {
				if r.Status == "FAILED" && !strings.Contains(r.FailureMessage, tt.messageContains) {
					t.Errorf("failure message %q does not contain %q", r.FailureMessage, tt.messageContains)
				}
			}
		})
	}
}

func TestParseJUnitRejectsBadInput(t *testing.T) {
	tests := []struct {
		name string
		body string
	}{
		{"empty", ""},
		{"not xml", "hello"},
		{"html page", "<html><body>502 Bad Gateway</body></html>"},
		{"no test cases", `<testsuite name="empty"></testsuite>`},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if _, err := ParseJUnit([]byte(tt.body)); err == nil {
				t.Errorf("expected an error for %q", tt.body)
			}
		})
	}
}

func TestTruncateKeepsWholeCharacters(t *testing.T) {
	got := truncate("ทดสอบ", 3)
	if got != "ทดส" {
		t.Errorf("truncate = %q, want %q", got, "ทดส")
	}
}

func TestParseJUnitReadsCombinedFiles(t *testing.T) {
	combined := `<testsuites>
  <testsuite name="A"><testcase classname="A" name="one()" time="0.001"/></testsuite>
  <testsuites name="pytest">
    <testsuite name="pytest"><testcase classname="tests.b" name="test_two" time="0.002"/></testsuite>
  </testsuites>
</testsuites>`

	got, err := ParseJUnit([]byte(combined))
	if err != nil {
		t.Fatal(err)
	}
	want := []row{
		{"A", "one()", "PASSED", 1},
		{"tests.b", "test_two", "PASSED", 2},
	}
	if !reflect.DeepEqual(rows(got), want) {
		t.Errorf("results:\n got  %+v\n want %+v", rows(got), want)
	}
}