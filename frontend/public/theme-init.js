// Applies the saved theme before the first paint, so a person who chose the dark theme never sees a white flash.
// It is a separate file, not an inline script, because the content security policy allows scripts from our origin only.
// Keep it in step with src/lib/theme/theme.ts.
;(function () {
  try {
    var saved = localStorage.getItem('paraphe-theme')
    var dark = saved === 'dark' || (saved === 'system' && matchMedia('(prefers-color-scheme: dark)').matches)
    if (dark) document.documentElement.classList.add('dark')
  } catch (e) {
    /* storage can be blocked: the light theme is the default */
  }
})()
