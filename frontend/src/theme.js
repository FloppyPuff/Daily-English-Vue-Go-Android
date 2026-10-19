// Dark mode follows the learner's local clock: 19:00–07:00. The inline script
// in index.html applies the first value before paint, this keeps it current.
export const DARK_START_HOUR = 19;
export const DARK_END_HOUR = 7;

export function isDarkHour(hour) {
  return hour >= DARK_START_HOUR || hour < DARK_END_HOUR;
}

export function applyAutoTheme(now = new Date()) {
  const dark = isDarkHour(now.getHours());
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', dark ? '#141816' : '#22352b');
  return dark;
}

export function startAutoTheme() {
  applyAutoTheme();
  const timer = setInterval(() => applyAutoTheme(), 60_000);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) applyAutoTheme();
  });
  window.addEventListener('focus', () => applyAutoTheme());
  return () => clearInterval(timer);
}
