/**
 * `<input type="datetime-local">` and `<input type="date">` speak the browser's
 * own wall clock. `toISOString()` speaks UTC, so a Los Angeles evening produced
 * a minimum seven hours in the future and a Tokyo morning one nine hours in the
 * past — invisible in UTC, where the two agree (R53-10).
 */
const pad = (n: number): string => String(n).padStart(2, '0');

/** Today in the browser's time zone, as `YYYY-MM-DD`. */
export function localDateValue(date: Date = new Date()): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** Now in the browser's time zone, as the `YYYY-MM-DDTHH:mm` an input expects. */
export function localDateTimeInputValue(date: Date = new Date()): string {
  return `${localDateValue(date)}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
