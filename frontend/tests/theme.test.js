import { test, expect } from 'vitest';
import { isDarkHour } from '../src/theme.js';

test('night mode runs from 19:00 through 06:59', () => {
  expect(isDarkHour(0)).toBe(true);
  expect(isDarkHour(6)).toBe(true);
  expect(isDarkHour(7)).toBe(false);
  expect(isDarkHour(12)).toBe(false);
  expect(isDarkHour(18)).toBe(false);
  expect(isDarkHour(19)).toBe(true);
  expect(isDarkHour(23)).toBe(true);
});
