const fs = require('fs');
const path = require('path');
const { loadApp, requireApp, APP_PATH } = require('./setup/loadApp');

const THEME_KEY = 'ops-dashboard.theme';
const CSS_PATH = path.join(path.dirname(APP_PATH), 'style.css');

function html(document) {
  return document.documentElement;
}

function toggle(document) {
  return document.getElementById('theme-toggle');
}

describe('theme toggle (TODO-231)', () => {
  afterEach(() => {
    jest.restoreAllMocks();
  });

  test('is a plain button in the header', async () => {
    const { document } = await loadApp();
    const button = document.querySelector('#app-header #theme-toggle');
    expect(button).not.toBeNull();
    expect(button.tagName).toBe('BUTTON');
    expect(button.getAttribute('type')).toBe('button');
  });

  test('defaults to the light theme when nothing is stored, and offers dark', async () => {
    const { document } = await loadApp();
    expect(html(document).getAttribute('data-theme')).toBe('light');
    expect(toggle(document).textContent).toBe('Dark theme');
    expect(toggle(document).getAttribute('aria-label')).toBe('Switch to dark theme');
  });

  test('clicking switches to dark, clicking again switches back to light', async () => {
    const { document } = await loadApp();
    toggle(document).click();
    expect(html(document).getAttribute('data-theme')).toBe('dark');
    expect(toggle(document).textContent).toBe('Light theme');
    expect(toggle(document).getAttribute('aria-label')).toBe('Switch to light theme');

    toggle(document).click();
    expect(html(document).getAttribute('data-theme')).toBe('light');
    expect(toggle(document).textContent).toBe('Dark theme');
  });

  test('toggling neither calls the API nor redraws the charts', async () => {
    const { document, api } = await loadApp();
    const callsBefore = api.calls.length;
    const onTimeBar = document.querySelector('#chart-on-time rect.bar');
    const ticketsBar = document.querySelector('#chart-tickets rect.bar');

    toggle(document).click();

    expect(api.calls.length).toBe(callsBefore);
    expect(onTimeBar.isConnected).toBe(true);
    expect(ticketsBar.isConnected).toBe(true);
  });

  test('the choice is saved in localStorage', async () => {
    const { document } = await loadApp();
    toggle(document).click();
    expect(window.localStorage.getItem(THEME_KEY)).toBe('dark');
    toggle(document).click();
    expect(window.localStorage.getItem(THEME_KEY)).toBe('light');
  });

  test('a stored theme is restored on load', async () => {
    const { document } = await loadApp({ localStorage: { [THEME_KEY]: 'dark' } });
    expect(html(document).getAttribute('data-theme')).toBe('dark');
    expect(toggle(document).textContent).toBe('Light theme');
  });

  test('an unknown stored value falls back to light', async () => {
    const { document } = await loadApp({ localStorage: { [THEME_KEY]: 'purple' } });
    expect(html(document).getAttribute('data-theme')).toBe('light');
    expect(toggle(document).textContent).toBe('Dark theme');
  });

  test('still loads and toggles when localStorage throws', async () => {
    jest.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });
    jest.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });
    const { document, app } = await loadApp();
    expect(app.state.error).toBeNull();
    expect(html(document).getAttribute('data-theme')).toBe('light');
    toggle(document).click();
    expect(html(document).getAttribute('data-theme')).toBe('dark');
  });
});

describe('theme helpers', () => {
  test('normalizeTheme keeps light and dark and maps anything else to light', () => {
    const { normalizeTheme } = requireApp();
    expect(normalizeTheme('light')).toBe('light');
    expect(normalizeTheme('dark')).toBe('dark');
    expect(normalizeTheme(null)).toBe('light');
    expect(normalizeTheme('DARK')).toBe('light');
  });

  test('otherTheme flips between light and dark', () => {
    const { otherTheme } = requireApp();
    expect(otherTheme('light')).toBe('dark');
    expect(otherTheme('dark')).toBe('light');
  });
});

// ---------- Static checks on style.css and app.js (AC-2) ----------

const COLOUR_LITERAL = /#[0-9a-f]{3,8}\b|\b(?:rgb|rgba|hsl|hsla)\s*\(/i;

/** [{ selector, body }] for every rule in a flat stylesheet (comments removed). */
function cssRules(css) {
  const rules = [];
  const re = /([^{}]+)\{([^{}]*)\}/g;
  let match;
  const stripped = css.replace(/\/\*[\s\S]*?\*\//g, '');
  while ((match = re.exec(stripped)) !== null) {
    rules.push({ selector: match[1].trim(), body: match[2] });
  }
  return rules;
}

function isThemeBlock(selector) {
  return selector.split(',').every((s) => /^:root(\[data-theme="(light|dark)"\])?$/.test(s.trim()));
}

function variables(body) {
  const vars = {};
  const re = /(--[\w-]+)\s*:\s*([^;]+);/g;
  let match;
  while ((match = re.exec(body)) !== null) {
    vars[match[1]] = match[2].trim();
  }
  return vars;
}

function themeVariables(theme) {
  const rules = cssRules(fs.readFileSync(CSS_PATH, 'utf8'));
  const block = rules.find((r) => isThemeBlock(r.selector) && r.selector.includes(`[data-theme="${theme}"]`));
  return block ? variables(block.body) : {};
}

function luminance(hex) {
  let h = hex.replace('#', '');
  if (h.length === 3) {
    h = h.split('').map((c) => c + c).join('');
  }
  const [r, g, b] = [0, 2, 4].map((i) => {
    const c = parseInt(h.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(a, b) {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}

describe('theme stylesheet', () => {
  test('colour literals only appear inside the theme variable blocks', () => {
    const offenders = cssRules(fs.readFileSync(CSS_PATH, 'utf8'))
      .filter((r) => !isThemeBlock(r.selector) && COLOUR_LITERAL.test(r.body))
      .map((r) => r.selector);
    expect(offenders).toEqual([]);
  });

  test('the dark theme defines every variable the light theme defines', () => {
    const light = Object.keys(themeVariables('light')).sort();
    const dark = Object.keys(themeVariables('dark')).sort();
    expect(light.length).toBeGreaterThan(0);
    expect(dark).toEqual(light);
  });

  test('both charts take their colours from variables', () => {
    const rules = cssRules(fs.readFileSync(CSS_PATH, 'utf8'));
    for (const selector of ['.chart-svg .bar', '.chart-svg .bar.warn', '.chart-svg .bar-label', '.chart-svg .bar-value']) {
      const rule = rules.find((r) => r.selector === selector);
      expect(rule).toBeDefined();
      expect(rule.body).toMatch(/fill:\s*var\(--[\w-]+\)/);
    }
  });

  describe.each(['light', 'dark'])('%s theme stays readable', (theme) => {
    const v = themeVariables(theme);

    test.each([
      ['--text', '--bg'],
      ['--text', '--surface'],
      ['--text-muted', '--surface'],
      ['--heading', '--surface'],
      ['--bar-label', '--surface'],
      ['--bar-value', '--surface'],
      ['--on-accent', '--accent'],
      ['--error', '--bg'],
      ['--renewal-text', '--renewal-bg']
    ])('%s on %s is at least 4.5:1', (fg, bg) => {
      expect(contrast(v[fg], v[bg])).toBeGreaterThanOrEqual(4.5);
    });

    test.each([
      ['--bar', '--surface'],
      ['--bar-warn', '--surface']
    ])('%s on %s is at least 3:1', (fg, bg) => {
      expect(contrast(v[fg], v[bg])).toBeGreaterThanOrEqual(3);
    });
  });
});

describe('app.js and colours', () => {
  test('contains no colour values', () => {
    const source = fs.readFileSync(APP_PATH, 'utf8');
    expect(source).not.toMatch(COLOUR_LITERAL);
    expect(source).not.toMatch(/['"](fill|stroke|color|background)['"]\s*:/);
    expect(source).not.toMatch(/\.style\./);
  });
});
