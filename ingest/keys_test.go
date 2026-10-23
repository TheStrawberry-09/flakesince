package main

import (
	"context"
	"testing"
	"time"
)

type fakeChecker struct {
	projects map[string]int64
	calls    int
}

func (f *fakeChecker) Check(_ context.Context, rawKey string) (int64, error) {
	f.calls++
	id, ok := f.projects[rawKey]
	if !ok {
		return 0, ErrInvalidKey
	}
	return id, nil
}

func TestHashKeyMatchesTheSpringApi(t *testing.T) {
	got := hashKey("abc")
	want := "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
	if got != want {
		t.Errorf("hashKey = %s, want %s", got, want)
	}
}

func TestCacheAvoidsRepeatedLookupsUntilExpiry(t *testing.T) {
	fake := &fakeChecker{projects: map[string]int64{"fs_live_good": 7}}
	cache := newCachingKeyChecker(fake, time.Minute)
	now := time.Date(2026, 10, 24, 9, 0, 0, 0, time.UTC)
	cache.now = func() time.Time { return now }

	for range 3 {
		if id, err := cache.Check(context.Background(), "fs_live_good"); err != nil || id != 7 {
			t.Fatalf("Check = %d, %v; want 7, nil", id, err)
		}
	}
	if fake.calls != 1 {
		t.Errorf("lookups = %d, want 1", fake.calls)
	}

	now = now.Add(61 * time.Second)
	if _, err := cache.Check(context.Background(), "fs_live_good"); err != nil {
		t.Fatal(err)
	}
	if fake.calls != 2 {
		t.Errorf("lookups after expiry = %d, want 2", fake.calls)
	}
}

func TestCacheDoesNotStoreInvalidKeys(t *testing.T) {
	fake := &fakeChecker{projects: map[string]int64{}}
	cache := newCachingKeyChecker(fake, time.Minute)

	for range 2 {
		if _, err := cache.Check(context.Background(), "fs_live_wrong"); err != ErrInvalidKey {
			t.Fatalf("err = %v, want ErrInvalidKey", err)
		}
	}
	if fake.calls != 2 {
		t.Errorf("lookups = %d, want 2", fake.calls)
	}
}