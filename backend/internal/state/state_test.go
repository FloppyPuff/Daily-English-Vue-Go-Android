package state

import (
	"encoding/json"
	"strings"
	"testing"
)

const baseState = `{
  "version": 3,
  "settings": {"exam":"cet6","dailyGoal":20,"sentenceGoal":3,"focusMinutes":25},
  "records": {},
  "sentenceRecords": {},
  "papers": {},
  "drafts": {},
  "savedWords": [],
  "savedSentences": [],
  "events": []
}`

func TestCreateStateDefaults(t *testing.T) {
	s := CreateState()
	if s.Version != 3 || s.Settings.Exam != "cet6" || s.Settings.DailyGoal != 20 {
		t.Fatalf("unexpected defaults: %+v", s)
	}
	if s.Records == nil || s.Events == nil {
		t.Fatal("maps and slices must be non-nil so JSON emits {} and []")
	}
}

func TestParseValidStateAndRoundTrip(t *testing.T) {
	s, err := ParseBackup(baseState)
	if err != nil {
		t.Fatal(err)
	}
	if s.Version != 3 || s.Settings.SentenceGoal != 3 {
		t.Fatalf("unexpected state: %+v", s)
	}
	encoded, err := json.Marshal(s)
	if err != nil {
		t.Fatal(err)
	}
	again, err := ParsePayload(encoded)
	if err != nil {
		t.Fatal(err)
	}
	if again.Settings.FocusMinutes != 25 {
		t.Fatalf("round trip lost settings: %+v", again.Settings)
	}
}

func TestVersionOneLegacyIDsAreMapped(t *testing.T) {
	raw := `{
      "version": 1,
      "settings": {"exam":"cet6","dailyGoal":20,"sentenceGoal":3,"focusMinutes":25},
      "records": {"w001":{"attempts":1,"interval":3,"due":1000,"streak":1,"lapses":0,"difficult":false,"lastReview":999}},
      "sentenceRecords": {},
      "drafts": {},
      "savedWords": ["w001","w001","custom-word"],
      "savedSentences": [],
      "events": [{"type":"word","id":"w001","quality":"good","at":999,"exam":"cet6","isNew":false}]
    }`
	s, err := ParseBackup(raw)
	if err != nil {
		t.Fatal(err)
	}
	if _, ok := s.Records["sustain"]; !ok {
		t.Fatalf("legacy record was not mapped: %+v", s.Records)
	}
	if len(s.SavedWords) != 2 || s.SavedWords[0] != "sustain" || s.SavedWords[1] != "custom-word" {
		t.Fatalf("legacy saved words not deduped/mapped: %+v", s.SavedWords)
	}
	if s.Events[0].ID == nil || *s.Events[0].ID != "sustain" {
		t.Fatalf("word event ids must follow the legacy mapping: %+v", s.Events[0])
	}
}

func TestMissingPapersIsAllowedButNullIsNot(t *testing.T) {
	withoutPapers := strings.Replace(baseState, `"papers": {},`, "", 1)
	if _, err := ParseBackup(withoutPapers); err != nil {
		t.Fatalf("missing papers should be accepted: %v", err)
	}
	withNull := strings.Replace(baseState, `"papers": {},`, `"papers": null,`, 1)
	if _, err := ParseBackup(withNull); err == nil {
		t.Fatal("null papers must be rejected")
	}
}

func TestInvalidFieldsAreRejected(t *testing.T) {
	cases := map[string]string{
		"version":  strings.Replace(baseState, `"version": 3`, `"version": 99`, 1),
		"exam":     strings.Replace(baseState, `"exam":"cet6"`, `"exam":"gre"`, 1),
		"goal":     strings.Replace(baseState, `"dailyGoal":20`, `"dailyGoal":0`, 1),
		"records":  strings.Replace(baseState, `"records": {}`, `"records": null`, 1),
		"events":   strings.Replace(baseState, `"events": []`, `"events": null`, 1),
		"draftKey": strings.Replace(baseState, `"drafts": {}`, `"drafts": {"bad id":"x"}`, 1),
	}
	for name, raw := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := ParseBackup(raw); err == nil {
				t.Fatalf("expected %s to fail", name)
			}
		})
	}
}

func TestPaperAndEventValidation(t *testing.T) {
	raw := `{
      "version": 3,
      "settings": {"exam":"postgrad","dailyGoal":10,"sentenceGoal":2,"focusMinutes":30},
      "records": {},
      "sentenceRecords": {},
      "papers": {"2024-1#0-0":{"answers":{"1":"D"},"correct":1,"total":1,"wrong":[],"words":["despite"],"rating":"mastered","at":100}},
      "drafts": {},
      "savedWords": [],
      "savedSentences": [],
      "events": [
        {"type":"paper","id":"2024-1#0-0","at":100,"exam":"postgrad","correct":1,"total":1},
        {"type":"focus","at":101,"exam":"postgrad","seconds":120}
      ]
    }`
	s, err := ParseBackup(raw)
	if err != nil {
		t.Fatal(err)
	}
	if s.Papers["2024-1#0-0"].Correct != 1 || s.Events[1].Seconds == nil || *s.Events[1].Seconds != 120 {
		t.Fatalf("paper/event not retained: %+v", s)
	}
	if _, err := ParseBackup(strings.Replace(raw, `"correct":1,"total":1,"wrong"`, `"correct":2,"total":1,"wrong"`, 1)); err == nil {
		t.Fatal("correct > total must fail")
	}
	if _, err := ParseBackup(strings.Replace(raw, `"seconds":120`, `"seconds":7201`, 1)); err == nil {
		t.Fatal("focus over two hours must fail")
	}
}

func TestTrailingJSONIsRejected(t *testing.T) {
	if _, err := ParseBackup(baseState + ` {}`); err == nil {
		t.Fatal("trailing JSON must fail")
	}
}
