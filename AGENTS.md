# Grade 7 project rules

- This repository is exclusively `nranjbar/oloomyar7`. All Grade 7 code, commits, builds and review assets belong here.
- The user explicitly forbids further changes to the Grade 9 project. Do not modify `nranjbar/OloomYar789`, its branches, pull requests, workflows or files while working on Grade 7.
- Preserve the copied UI components and interaction behavior. Display the original workbook question before its figures and interactive answer.
- Current scope is chapters 1 and 2 of the Grade 7 workbook, edition 1405, supplied in `haftom.zip` with the separate chapter answer PDFs.
- Keep source question numbering, all subparts, hints, correct alternatives, units and explanations accurate. Do not add invented source questions or source figures.
- Question images live in `app/src/main/assets/images`. Preserve filenames when the user supplies better replacements. The image for chapter 2 question 9 needs consistent labels: 36 g, 60 and 72 cubic centimetres.
- Keep `review/` assets and answer-image recommendations synchronized with app content. Avoid linking a missing answer-image file.
- Validate content with `python3 scripts/validate_grade7.py`. Use the repository's Grade 7 Review workflow for Android builds and native UI checks when application code changes.
