export function selectLocalVoice(voices) {
  return voices.find(v => v.localService && v.lang === 'en-GB') || voices.find(v => v.localService && /^en[-_]/i.test(v.lang));
}
