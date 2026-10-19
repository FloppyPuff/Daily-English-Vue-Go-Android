// Package state is the server-side copy of the study-state contract. It is
// deliberately strict: a stored state is read through the same validation used
// on write, so a damaged row becomes an empty plan instead of a crash.
package state

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"math"
	"regexp"
	"strings"
	"unicode/utf16"
)

var ErrInvalid = errors.New("invalid state")

const (
	StateVersion = 3
	MaxEvents    = 100000
	MaxText      = 10000
	maxBackup    = 5 * 1024 * 1024
)

var (
	wordID     = regexp.MustCompile(`^(?:w\d{3}|[a-z][a-z'-]{1,31})$`)
	sentenceID = regexp.MustCompile(`^(?:s\d{3}|\d{4}-[12]#\d{1,3}|cet6-\d{6}-\d{1,2})$`)
	paperKey   = regexp.MustCompile(`^\d{4}-[12]#\d{1,2}-\d{1,2}$`)
	answerKey  = regexp.MustCompile(`^\d{1,3}$`)
)

type Settings struct {
	Exam         string `json:"exam"`
	DailyGoal    int    `json:"dailyGoal"`
	SentenceGoal int    `json:"sentenceGoal"`
	FocusMinutes int    `json:"focusMinutes"`
}

type WordRecord struct {
	Attempts   float64 `json:"attempts"`
	Interval   float64 `json:"interval"`
	Due        float64 `json:"due"`
	Streak     float64 `json:"streak"`
	Lapses     float64 `json:"lapses"`
	Difficult  bool    `json:"difficult"`
	LastReview float64 `json:"lastReview"`
}

type SentenceRecord struct {
	Answer string  `json:"answer"`
	Rating string  `json:"rating"`
	At     float64 `json:"at"`
}

type PaperRecord struct {
	Answers map[string]string `json:"answers"`
	Correct float64           `json:"correct"`
	Total   float64           `json:"total"`
	Wrong   []int             `json:"wrong"`
	Words   []string          `json:"words"`
	Rating  string            `json:"rating"`
	At      float64           `json:"at"`
}

type StudyEvent struct {
	Type    string   `json:"type"`
	ID      *string  `json:"id,omitempty"`
	Quality *string  `json:"quality,omitempty"`
	Rating  *string  `json:"rating,omitempty"`
	Seconds *float64 `json:"seconds,omitempty"`
	Correct *float64 `json:"correct,omitempty"`
	Total   *float64 `json:"total,omitempty"`
	At      float64  `json:"at"`
	Exam    string   `json:"exam"`
	IsNew   *bool    `json:"isNew,omitempty"`
}

type AppState struct {
	Version         int                       `json:"version"`
	Settings        Settings                  `json:"settings"`
	Records         map[string]WordRecord     `json:"records"`
	SentenceRecords map[string]SentenceRecord `json:"sentenceRecords"`
	Papers          map[string]PaperRecord    `json:"papers"`
	Drafts          map[string]string         `json:"drafts"`
	SavedWords      []string                  `json:"savedWords"`
	SavedSentences  []string                  `json:"savedSentences"`
	Events          []StudyEvent              `json:"events"`
}

func CreateState() *AppState {
	return &AppState{
		Version:         StateVersion,
		Settings:        Settings{Exam: "cet6", DailyGoal: 20, SentenceGoal: 3, FocusMinutes: 25},
		Records:         map[string]WordRecord{},
		SentenceRecords: map[string]SentenceRecord{},
		Papers:          map[string]PaperRecord{},
		Drafts:          map[string]string{},
		SavedWords:      []string{},
		SavedSentences:  []string{},
		Events:          []StudyEvent{},
	}
}

// ParseBackup validates and normalises a backup/state JSON string. It mirrors
// the browser's parseBackup, including version 1 legacy word ids.
func ParseBackup(raw string) (*AppState, error) {
	if utf16Len(raw) > maxBackup {
		return nil, ErrInvalid
	}
	dec := json.NewDecoder(strings.NewReader(raw))
	dec.UseNumber()
	var value any
	if err := dec.Decode(&value); err != nil {
		return nil, ErrInvalid
	}
	var extra any
	if err := dec.Decode(&extra); err != io.EOF {
		return nil, ErrInvalid
	}
	return ParseValue(value)
}

func ParsePayload(raw []byte) (*AppState, error) {
	if len(raw) > maxBackup {
		return nil, ErrInvalid
	}
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	var value any
	if err := dec.Decode(&value); err != nil {
		return nil, ErrInvalid
	}
	var extra any
	if err := dec.Decode(&extra); err == nil {
		return nil, ErrInvalid
	}
	return ParseValue(value)
}

func ParseValue(value any) (*AppState, error) {
	obj, ok := value.(map[string]any)
	if !ok {
		return nil, ErrInvalid
	}
	version, ok := integer(obj["version"])
	if !ok || version < 1 || version > StateVersion {
		return nil, ErrInvalid
	}
	settingsObj, ok := obj["settings"].(map[string]any)
	if !ok {
		return nil, ErrInvalid
	}
	settings, ok := parseSettings(settingsObj)
	if !ok {
		return nil, ErrInvalid
	}
	result := CreateState()
	result.Settings = settings

	recordsObj, ok := obj["records"].(map[string]any)
	if !ok {
		return nil, ErrInvalid
	}
	for key, rawRecord := range recordsObj {
		if !isWordID(key) {
			return nil, ErrInvalid
		}
		recordObj, ok := rawRecord.(map[string]any)
		if !ok {
			return nil, ErrInvalid
		}
		record, ok := parseWordRecord(recordObj)
		if !ok {
			return nil, ErrInvalid
		}
		id := key
		if version == 1 {
			id = legacyWordID(id)
		}
		if kept, exists := result.Records[id]; !exists || kept.LastReview < record.LastReview {
			result.Records[id] = record
		}
	}

	sentenceObj, ok := obj["sentenceRecords"].(map[string]any)
	if !ok {
		return nil, ErrInvalid
	}
	for key, rawRecord := range sentenceObj {
		if !isSentenceID(key) {
			return nil, ErrInvalid
		}
		recordObj, ok := rawRecord.(map[string]any)
		if !ok {
			return nil, ErrInvalid
		}
		record, ok := parseSentenceRecord(recordObj)
		if !ok {
			return nil, ErrInvalid
		}
		result.SentenceRecords[key] = record
	}

	draftsObj, ok := obj["drafts"].(map[string]any)
	if !ok {
		return nil, ErrInvalid
	}
	for key, rawText := range draftsObj {
		if !isSentenceID(key) {
			return nil, ErrInvalid
		}
		text, ok := textValue(rawText)
		if !ok {
			return nil, ErrInvalid
		}
		result.Drafts[key] = text
	}

	if rawPapers, exists := obj["papers"]; exists {
		papersObj, ok := rawPapers.(map[string]any)
		if !ok {
			return nil, ErrInvalid
		}
		for key, rawPaper := range papersObj {
			if !isPaperKey(key) {
				return nil, ErrInvalid
			}
			paperObj, ok := rawPaper.(map[string]any)
			if !ok {
				return nil, ErrInvalid
			}
			paper, ok := parsePaperRecord(paperObj)
			if !ok {
				return nil, ErrInvalid
			}
			result.Papers[key] = paper
		}
	}

	savedWords, ok := obj["savedWords"].([]any)
	if !ok {
		return nil, ErrInvalid
	}
	seenWords := map[string]bool{}
	for _, raw := range savedWords {
		id, ok := raw.(string)
		if !ok || !isWordID(id) {
			return nil, ErrInvalid
		}
		if version == 1 {
			id = legacyWordID(id)
		}
		if !seenWords[id] {
			seenWords[id] = true
			result.SavedWords = append(result.SavedWords, id)
		}
	}

	savedSentences, ok := obj["savedSentences"].([]any)
	if !ok {
		return nil, ErrInvalid
	}
	seenSentences := map[string]bool{}
	for _, raw := range savedSentences {
		id, ok := raw.(string)
		if !ok || !isSentenceID(id) {
			return nil, ErrInvalid
		}
		if !seenSentences[id] {
			seenSentences[id] = true
			result.SavedSentences = append(result.SavedSentences, id)
		}
	}

	events, ok := obj["events"].([]any)
	if !ok || len(events) > MaxEvents {
		return nil, ErrInvalid
	}
	for _, raw := range events {
		eventObj, ok := raw.(map[string]any)
		if !ok {
			return nil, ErrInvalid
		}
		event, ok := parseEvent(eventObj, version)
		if !ok {
			return nil, ErrInvalid
		}
		result.Events = append(result.Events, event)
	}

	return result, nil
}

func parseSettings(obj map[string]any) (Settings, bool) {
	exam, ok := obj["exam"].(string)
	if !ok || (exam != "cet6" && exam != "postgrad") {
		return Settings{}, false
	}
	dailyGoal, ok := integer(obj["dailyGoal"])
	if !ok || dailyGoal < 1 || dailyGoal > 100 {
		return Settings{}, false
	}
	sentenceGoal, ok := integer(obj["sentenceGoal"])
	if !ok || sentenceGoal < 1 || sentenceGoal > 20 {
		return Settings{}, false
	}
	focusMinutes, ok := integer(obj["focusMinutes"])
	if !ok || focusMinutes < 1 || focusMinutes > 120 {
		return Settings{}, false
	}
	return Settings{Exam: exam, DailyGoal: dailyGoal, SentenceGoal: sentenceGoal, FocusMinutes: focusMinutes}, true
}

func parseWordRecord(obj map[string]any) (WordRecord, bool) {
	attempts, ok := number(obj["attempts"])
	if !ok {
		return WordRecord{}, false
	}
	interval, ok := number(obj["interval"])
	if !ok {
		return WordRecord{}, false
	}
	due, ok := timeNumber(obj["due"])
	if !ok {
		return WordRecord{}, false
	}
	streak, ok := number(obj["streak"])
	if !ok {
		return WordRecord{}, false
	}
	lapses, ok := number(obj["lapses"])
	if !ok {
		return WordRecord{}, false
	}
	difficult, ok := obj["difficult"].(bool)
	if !ok {
		return WordRecord{}, false
	}
	lastReview, ok := timeNumber(obj["lastReview"])
	if !ok {
		return WordRecord{}, false
	}
	return WordRecord{Attempts: attempts, Interval: interval, Due: due, Streak: streak, Lapses: lapses, Difficult: difficult, LastReview: lastReview}, true
}

func parseSentenceRecord(obj map[string]any) (SentenceRecord, bool) {
	answer, ok := textValue(obj["answer"])
	if !ok {
		return SentenceRecord{}, false
	}
	rating, ok := obj["rating"].(string)
	if !ok || (rating != "review" && rating != "mastered") {
		return SentenceRecord{}, false
	}
	at, ok := timeNumber(obj["at"])
	if !ok {
		return SentenceRecord{}, false
	}
	return SentenceRecord{Answer: answer, Rating: rating, At: at}, true
}

func parsePaperRecord(obj map[string]any) (PaperRecord, bool) {
	answersObj, ok := obj["answers"].(map[string]any)
	if !ok {
		return PaperRecord{}, false
	}
	answers := map[string]string{}
	for key, raw := range answersObj {
		if !answerKey.MatchString(key) {
			return PaperRecord{}, false
		}
		value, ok := textValue(raw)
		if !ok {
			return PaperRecord{}, false
		}
		answers[key] = value
	}
	correct, ok := number(obj["correct"])
	if !ok {
		return PaperRecord{}, false
	}
	total, ok := number(obj["total"])
	if !ok || correct > total {
		return PaperRecord{}, false
	}
	wrongRaw, ok := obj["wrong"].([]any)
	if !ok {
		return PaperRecord{}, false
	}
	wrong := make([]int, 0, len(wrongRaw))
	for _, raw := range wrongRaw {
		value, ok := integer(raw)
		if !ok || value < 1 || value > 100 {
			return PaperRecord{}, false
		}
		wrong = append(wrong, value)
	}
	wordsRaw, ok := obj["words"].([]any)
	if !ok {
		return PaperRecord{}, false
	}
	words := make([]string, 0, len(wordsRaw))
	seen := map[string]bool{}
	for _, raw := range wordsRaw {
		id, ok := raw.(string)
		if !ok || !isWordID(id) {
			return PaperRecord{}, false
		}
		if !seen[id] {
			seen[id] = true
			words = append(words, id)
		}
	}
	rating := ""
	if rawRating, exists := obj["rating"]; exists && rawRating != nil {
		value, ok := rawRating.(string)
		if !ok || (value != "" && value != "review" && value != "mastered") {
			return PaperRecord{}, false
		}
		rating = value
	}
	at, ok := timeNumber(obj["at"])
	if !ok {
		return PaperRecord{}, false
	}
	return PaperRecord{Answers: answers, Correct: correct, Total: total, Wrong: wrong, Words: words, Rating: rating, At: at}, true
}

func parseEvent(obj map[string]any, version int) (StudyEvent, bool) {
	eventType, ok := obj["type"].(string)
	if !ok {
		return StudyEvent{}, false
	}
	at, ok := timeNumber(obj["at"])
	if !ok {
		return StudyEvent{}, false
	}
	exam, ok := obj["exam"].(string)
	if !ok || (exam != "cet6" && exam != "postgrad") {
		return StudyEvent{}, false
	}
	event := StudyEvent{Type: eventType, At: at, Exam: exam}
	switch eventType {
	case "word":
		id, ok := obj["id"].(string)
		if !ok || !isWordID(id) {
			return StudyEvent{}, false
		}
		if version == 1 {
			id = legacyWordID(id)
		}
		quality, ok := obj["quality"].(string)
		if !ok || !oneOf(quality, "again", "hard", "good", "easy") {
			return StudyEvent{}, false
		}
		isNew, ok := obj["isNew"].(bool)
		if !ok {
			return StudyEvent{}, false
		}
		event.ID = &id
		event.Quality = &quality
		event.IsNew = &isNew
	case "sentence":
		id, ok := obj["id"].(string)
		if !ok || !isSentenceID(id) {
			return StudyEvent{}, false
		}
		rating, ok := obj["rating"].(string)
		if !ok || !oneOf(rating, "review", "mastered") {
			return StudyEvent{}, false
		}
		event.ID = &id
		event.Rating = &rating
	case "paper":
		id, ok := obj["id"].(string)
		if !ok || !isPaperKey(id) {
			return StudyEvent{}, false
		}
		correct, ok := number(obj["correct"])
		if !ok {
			return StudyEvent{}, false
		}
		total, ok := number(obj["total"])
		if !ok || correct > total {
			return StudyEvent{}, false
		}
		event.ID = &id
		event.Correct = &correct
		event.Total = &total
	case "focus":
		seconds, ok := number(obj["seconds"])
		if !ok || seconds > 7200 {
			return StudyEvent{}, false
		}
		event.Seconds = &seconds
	default:
		return StudyEvent{}, false
	}
	return event, true
}

func number(value any) (float64, bool) {
	switch v := value.(type) {
	case json.Number:
		f, err := v.Float64()
		return f, err == nil && !math.IsNaN(f) && !math.IsInf(f, 0) && f >= 0
	case float64:
		return v, !math.IsNaN(v) && !math.IsInf(v, 0) && v >= 0
	case int:
		return float64(v), v >= 0
	default:
		return 0, false
	}
}

func integer(value any) (int, bool) {
	f, ok := number(value)
	if !ok || f != math.Trunc(f) || f > math.MaxInt32 {
		return 0, false
	}
	return int(f), true
}

func timeNumber(value any) (float64, bool) {
	f, ok := number(value)
	return f, ok && f <= 8640000000000000
}

func textValue(value any) (string, bool) {
	text, ok := value.(string)
	return text, ok && utf16Len(text) <= MaxText
}

func isWordID(value string) bool     { return wordID.MatchString(value) }
func isSentenceID(value string) bool { return sentenceID.MatchString(value) }
func isPaperKey(value string) bool   { return paperKey.MatchString(value) }
func oneOf(value string, options ...string) bool {
	for _, option := range options {
		if value == option {
			return true
		}
	}
	return false
}

func utf16Len(value string) int {
	return len(utf16.Encode([]rune(value)))
}

func legacyWordID(id string) string {
	if mapped, ok := LegacyWordIDs[id]; ok {
		return mapped
	}
	return id
}

var LegacyWordIDs = map[string]string{
	"w001": "sustain", "w002": "perspective", "w003": "acquire", "w004": "adapt",
	"w005": "allocate", "w006": "assess", "w007": "assume", "w008": "benefit",
	"w009": "clarify", "w010": "coherent", "w011": "collaborate", "w012": "compensate",
	"w013": "comprehensive", "w014": "conclude", "w015": "conduct", "w016": "consequence",
	"w017": "consistent", "w018": "constitute", "w019": "constrain", "w020": "consume",
	"w021": "contribute", "w022": "conventional", "w023": "crucial", "w024": "decline",
	"w025": "demonstrate", "w026": "derive", "w027": "diminish", "w028": "diverse",
	"w029": "efficient", "w030": "emerge", "w031": "enhance", "w032": "establish",
	"w033": "evaluate", "w034": "evidence", "w035": "expand", "w036": "facilitate",
	"w037": "flexible", "w038": "fundamental", "w039": "generate", "w040": "hypothesis",
	"w041": "implement", "w042": "imply", "w043": "incentive", "w044": "indicate",
	"w045": "inevitable", "w046": "infrastructure", "w047": "innovation", "w048": "integrate",
	"w049": "interpret", "w050": "justify", "w051": "maintain", "w052": "objective",
	"w053": "participate", "w054": "potential", "w055": "priority", "w056": "reliable",
	"w057": "resource", "w058": "significant", "w059": "transform", "w060": "transition",
	"w061": "accessible", "w062": "affordable", "w063": "awareness", "w064": "biodiversity",
	"w065": "commute", "w066": "conservation", "w067": "employment", "w068": "cultivate",
	"w069": "engagement", "w070": "literacy", "w071": "renewable", "w072": "resilience",
	"w073": "volunteer", "w074": "welfare", "w075": "urbanization", "w076": "ambiguous",
	"w077": "empirical", "w078": "autonomy", "w079": "cognitive", "w080": "correlate",
	"w081": "discrepancy", "w082": "inference", "w083": "inherent", "w084": "paradigm",
	"w085": "premise", "w086": "reconcile", "w087": "rigorous", "w088": "scrutiny",
	"w089": "subordinate", "w090": "validity",
}
