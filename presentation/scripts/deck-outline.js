const fs = require('fs');
const src = fs.readFileSync(process.argv[2], 'utf8');
const block = src.split('/*SLIDES-START*/')[1].split('/*SLIDES-END*/')[0];
const { SECTIONS, SLIDES } = new Function(block + '; return { SECTIONS, SLIDES };')();
const total = SLIDES.reduce((a, s) => a + s.min, 0);
let out = `# Meet-up talk outline — "Single Responsibility Principle in AI: one door to the LLM"\n\n`;
out += `_Generated from \`deck.html\` (the \`SLIDES\` block) by \`node presentation/scripts/deck-outline.js presentation/meetup/deck.html presentation/meetup/outline.md\`. Edit notes in the deck, then regenerate._\n\n`;
out += `Planned length: **${total} min** · ${SLIDES.length} slides · English.\n\n`;
out += `Deck controls: → / Space / PageDown = next (reveals builds first) · ← = back · P = pause · R = restart animation · − / + = animation speed (0.25×–3×, remembered) · 0 = 1× · F = fullscreen · S = presenter notes window.\n\n`;
let sec = null, clock = 0;
const mm = m => `${Math.floor(m)}:${String(Math.round((m % 1) * 60)).padStart(2, '0')}`;
SLIDES.forEach((s, i) => {
  if (s.sec !== sec) {
    sec = s.sec;
    const secMin = SLIDES.filter(x => x.sec === sec).reduce((a, x) => a + x.min, 0);
    out += `## ${SECTIONS[sec].name} — ${secMin} min\n\n`;
  }
  out += `### ${i + 1}. ${s.title}  _(${s.min} min, starts at ${mm(clock)})_${s.scene ? '  🎞 looping animation' : ''}\n\n`;
  s.notes.forEach(n => out += `- ${n}\n`);
  if (s.joke) out += `\n> 😄 ${s.joke}\n`;
  out += '\n';
  clock += s.min;
});
fs.writeFileSync(process.argv[3], out);
console.log('total', total, 'slides', SLIDES.length);
